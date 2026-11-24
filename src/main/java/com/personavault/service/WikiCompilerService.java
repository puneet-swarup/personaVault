/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personavault.entity.Document;
import com.personavault.entity.Fact;
import com.personavault.entity.VaultEntity;
import com.personavault.entity.WikiPage;
import com.personavault.entity.WikiPageType;
import com.personavault.repository.DocumentRepository;
import com.personavault.repository.FactRepository;
import com.personavault.repository.VaultEntityRepository;
import com.personavault.repository.WikiPageRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Compiles Tier 2 facts into Tier 3 wiki pages (ADR-015, ADR-016, ADR-022).
 *
 * <p>Three entry points, matching the compilation model in ADR-022:
 * <ul>
 *   <li>{@link #compileEntity(Long)} — one page, used by the incremental path</li>
 *   <li>{@link #compileForDocument(Long)} — the pages a newly ingested document touches</li>
 *   <li>{@link #compileAll()} — the nightly safety net that catches drift</li>
 * </ul>
 *
 * <p>Each compile renders markdown from facts, validates provenance, writes the file
 * (disk is canonical), and upserts the {@code wiki_page} mirror row.
 */
@Slf4j
@Service
public class WikiCompilerService {

    /** Frontmatter version of a page compiled for the first time. */
    static final int FIRST_VERSION = 1;

    private final VaultEntityRepository entityRepository;
    private final FactRepository factRepository;
    private final DocumentRepository documentRepository;
    private final WikiPageRepository wikiPageRepository;
    private final WikiMarkdownRenderer renderer;
    private final WikiPageValidator validator;
    private final WikiIndexBuilder indexBuilder;
    private final WikiFileStore fileStore;
    private final Clock clock;

    /**
     * Constructor injection.
     *
     * @param entityRepository vault entities (ADR-017)
     * @param factRepository facts to render
     * @param documentRepository source documents, for footnote names
     * @param wikiPageRepository the wiki mirror table
     * @param renderer markdown renderer
     * @param validator provenance validator
     * @param indexBuilder index.md generator
     * @param fileStore markdown file store
     * @param clock clock for compile timestamps
     */
    public WikiCompilerService(
            VaultEntityRepository entityRepository,
            FactRepository factRepository,
            DocumentRepository documentRepository,
            WikiPageRepository wikiPageRepository,
            WikiMarkdownRenderer renderer,
            WikiPageValidator validator,
            WikiIndexBuilder indexBuilder,
            WikiFileStore fileStore,
            Clock clock) {
        this.entityRepository = entityRepository;
        this.factRepository = factRepository;
        this.documentRepository = documentRepository;
        this.wikiPageRepository = wikiPageRepository;
        this.renderer = renderer;
        this.validator = validator;
        this.indexBuilder = indexBuilder;
        this.fileStore = fileStore;
        this.clock = clock;
    }

    /**
     * Compiles the wiki page for one entity.
     *
     * <p>Returns empty when the entity is unknown, soft-deleted, or has no facts yet:
     * an empty page would be noise in the vault.
     *
     * @param entityId the entity to compile
     * @return the mirror row that was written, or empty when nothing was compiled
     * @throws WikiPageValidationException if the rendered page breaks the provenance rule
     */
    @Transactional
    public Optional<WikiPage> compileEntity(Long entityId) {
        Optional<VaultEntity> found = entityRepository.findById(entityId);
        if (found.isEmpty() || found.get().getDeletedAt() != null) {
            log.debug("Skipping wiki compile for unknown or deleted entity {}", entityId);
            return Optional.empty();
        }
        VaultEntity entity = found.get();
        List<Fact> facts = factRepository.findByEntityIdOrderByFactKeyAscValidFromAsc(entityId);
        if (facts.isEmpty()) {
            log.debug("Skipping wiki compile for entity {} — no facts to render", entityId);
            return Optional.empty();
        }

        String slug = WikiSlugger.entitySlug(entity.getEntityType(), entity.getCanonicalName());
        int version = wikiPageRepository
                .findById(slug)
                .map(existing -> existing.getVersion() + 1)
                .orElse(FIRST_VERSION);
        Instant now = Instant.now(clock);

        WikiRenderModel model = new WikiRenderModel(
                slug,
                WikiPageType.ENTITY,
                entity.getCanonicalName(),
                entity.getEntityType() + ":" + entity.getCanonicalName(),
                WikiMarkdownRenderer.needsReview(facts),
                version,
                now,
                facts,
                sourceNames(facts),
                entityLinks(facts));

        String markdown = renderer.render(model);
        validator.validate(markdown);
        fileStore.write(WikiPageType.ENTITY, slug, markdown);

        WikiPage page = wikiPageRepository.findById(slug).orElseGet(WikiPage::new);
        page.setSlug(slug);
        page.setPageType(WikiPageType.ENTITY);
        page.setTitle(entity.getCanonicalName());
        page.setBodyMarkdown(markdown);
        page.setEntityId(entity.getId());
        page.setNeedsReview(model.needsReview());
        page.setVersion(version);
        page.setLastCompiledAt(now);
        WikiPage saved = wikiPageRepository.save(page);

        rebuildIndex(now);
        log.info("Compiled wiki page '{}' (v{}, {} facts)", slug, version, facts.size());
        return Optional.of(saved);
    }

    /**
     * Compiles every page touched by a document's facts.
     *
     * <p>This is the incremental path: an upload recompiles only the entities whose
     * facts came from that document, so ingest stays fast as the vault grows.
     *
     * @param documentId the freshly ingested document
     * @return how many pages were compiled
     */
    @Transactional
    public int compileForDocument(Long documentId) {
        Set<Long> entityIds = new LinkedHashSet<>();
        for (Fact fact : factRepository.findBySourceDocId(documentId)) {
            if (fact.getEntityId() != null) {
                entityIds.add(fact.getEntityId());
            }
        }
        int compiled = 0;
        for (Long entityId : entityIds) {
            if (compileEntity(entityId).isPresent()) {
                compiled++;
            }
        }
        log.debug("Compiled {} wiki page(s) for document {}", compiled, documentId);
        return compiled;
    }

    /**
     * Recompiles every page from live facts.
     *
     * <p>Runs nightly as a safety net (ADR-022): it picks up entities added by the
     * legacy backfill, repairs pages whose facts changed out of band, and refreshes
     * the index.
     *
     * @return how many pages were compiled
     */
    @Scheduled(cron = "${personavault.wiki.recompile-cron:0 30 2 * * *}")
    @Transactional
    public int compileAll() {
        int compiled = 0;
        for (VaultEntity entity : entityRepository.findAll()) {
            if (entity.getDeletedAt() == null && compileEntity(entity.getId()).isPresent()) {
                compiled++;
            }
        }
        rebuildIndex(Instant.now(clock));
        log.info("Nightly wiki recompile finished: {} page(s)", compiled);
        return compiled;
    }

    // Footnote text needs the original file name, not the internal document id.
    private Map<Long, String> sourceNames(List<Fact> facts) {
        Set<Long> documentIds = new LinkedHashSet<>();
        for (Fact fact : facts) {
            if (fact.getSourceDocId() != null) {
                documentIds.add(fact.getSourceDocId());
            }
        }
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (Document document : documentRepository.findAllById(documentIds)) {
            names.put(document.getId(), document.getFileName());
        }
        return names;
    }

    // Entity references become [[wikilinks]] pointing at the target's page.
    private Map<Long, WikiRenderModel.WikiLink> entityLinks(List<Fact> facts) {
        Set<Long> targetIds = new LinkedHashSet<>();
        for (Fact fact : facts) {
            if (fact.getValueEntityId() != null) {
                targetIds.add(fact.getValueEntityId());
            }
        }
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, WikiRenderModel.WikiLink> links = new HashMap<>();
        for (VaultEntity target : entityRepository.findAllById(targetIds)) {
            links.put(
                    target.getId(),
                    new WikiRenderModel.WikiLink(
                            WikiSlugger.entitySlug(target.getEntityType(), target.getCanonicalName()),
                            Objects.requireNonNullElse(target.getCanonicalName(), "untitled")));
        }
        return links;
    }

    private void rebuildIndex(Instant now) {
        List<WikiIndexEntry> entries = wikiPageRepository.findAllByOrderByPageTypeAscTitleAsc().stream()
                .map(page -> new WikiIndexEntry(page.getSlug(), page.getPageType(), page.getTitle()))
                .toList();
        fileStore.writeIndex(indexBuilder.render(entries, now));
    }
}
