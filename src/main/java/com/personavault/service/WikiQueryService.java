/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.personavault.entity.WikiPage;
import com.personavault.entity.WikiPageType;
import com.personavault.repository.WikiPageRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Finds the wiki page that can answer a question (Tier 3 retrieval, ADR-016).
 *
 * <p>This is where the vault starts paying for itself: one indexed read of the
 * {@code wiki_page} mirror, in-memory scoring, then a single file read — instead of
 * embedding the question and scanning vectors for every query. The page body that goes
 * to the LLM is read from disk, because disk is canonical (ADR-022).
 *
 * <p>Scoring is token overlap with inverse-document-frequency weighting, so a token that
 * appears in every policy page ("policy") counts for little and a distinguishing token
 * (a policy number, an insurer name) counts for a lot. There is no embedding call and no
 * LLM in this path, which keeps it fast and testable.
 */
@Slf4j
@Service
public class WikiQueryService {

    /** Words too short or too common to identify a page. */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "my", "me", "i", "of", "for", "is", "are", "was", "were", "do", "does", "did", "what",
            "when", "how", "has", "have", "had", "to", "in", "on", "and", "or", "about", "with", "from", "by", "at",
            "be", "been", "it", "this", "that", "you", "your", "can", "could", "would", "should", "tell", "give",
            "show", "know", "all", "any", "there", "their", "them", "much", "many", "over", "across", "years", "time");

    /** Shortest token that can identify a page. */
    private static final int MIN_TOKEN_LENGTH = 3;

    /** Where a token was found, used to weight the match. */
    private static final double TITLE_WEIGHT = 3.0;

    private static final double SLUG_WEIGHT = 2.0;
    private static final double BODY_WEIGHT = 1.0;

    /** Rarity weighting: a token found on one page is the most useful signal. */
    private static final double RARE_WEIGHT = 1.0;

    private static final double UNCOMMON_WEIGHT = 0.5;
    private static final double COMMON_WEIGHT = 0.2;
    private static final int UNCOMMON_MAX_DOCUMENTS = 3;

    /** A token found on exactly this many pages is unique, and the strongest signal. */
    private static final int UNIQUE_DOCUMENT_COUNT = 1;

    private final WikiPageRepository wikiPageRepository;
    private final WikiFileStore fileStore;

    /**
     * Constructor injection.
     *
     * @param wikiPageRepository the mirror table, used to find candidate pages
     * @param fileStore reads the canonical markdown for the winning page
     */
    public WikiQueryService(WikiPageRepository wikiPageRepository, WikiFileStore fileStore) {
        this.wikiPageRepository = wikiPageRepository;
        this.fileStore = fileStore;
    }

    /**
     * Returns the page that best matches a question.
     *
     * @param question the user's question
     * @return the best page, or empty when nothing matches well enough to answer from
     */
    public Optional<WikiPageContext> findPage(String question) {
        List<String> tokens = tokenize(question);
        if (tokens.isEmpty()) {
            return Optional.empty();
        }
        List<WikiPage> candidates = wikiPageRepository.findAllByOrderByPageTypeAscTitleAsc().stream()
                .filter(page -> page.getPageType() != WikiPageType.INDEX)
                .toList();
        if (candidates.isEmpty()) {
            log.debug("Wiki lookup skipped: vault has no compiled pages");
            return Optional.empty();
        }

        WikiPage best = null;
        double bestScore = 0;
        for (WikiPage page : candidates) {
            double score = score(page, tokens, candidates);
            if (score > bestScore) {
                best = page;
                bestScore = score;
            }
        }
        if (best == null) {
            log.debug("Wiki lookup found no page matching tokens {}", tokens);
            return Optional.empty();
        }
        log.debug("Wiki lookup chose '{}' with score {}", best.getSlug(), bestScore);
        return Optional.of(toContext(best));
    }

    private WikiPageContext toContext(WikiPage page) {
        String markdown = fileStore.read(page.getPageType(), page.getSlug()).orElseGet(() -> {
            log.warn("Wiki mirror row '{}' has no file on disk; using the mirrored body", page.getSlug());
            return page.getBodyMarkdown();
        });
        return new WikiPageContext(page.getSlug(), page.getTitle(), markdown);
    }

    // Weighted overlap: rare tokens found in the title beat common tokens found anywhere.
    private static double score(WikiPage page, List<String> tokens, List<WikiPage> candidates) {
        String title = lower(page.getTitle());
        String slug = lower(page.getSlug());
        String body = lower(page.getBodyMarkdown());
        double total = 0;
        for (String token : tokens) {
            double position = 0;
            if (title.contains(token)) {
                position += TITLE_WEIGHT;
            }
            if (slug.contains(token)) {
                position += SLUG_WEIGHT;
            }
            if (body.contains(token)) {
                position += BODY_WEIGHT;
            }
            if (position > 0) {
                total += position * rarity(token, candidates);
            }
        }
        return total;
    }

    private static double rarity(String token, List<WikiPage> candidates) {
        long documentFrequency = candidates.stream()
                .filter(page -> lower(page.getTitle()).contains(token)
                        || lower(page.getSlug()).contains(token)
                        || lower(page.getBodyMarkdown()).contains(token))
                .count();
        if (documentFrequency <= UNIQUE_DOCUMENT_COUNT) {
            return RARE_WEIGHT;
        }
        return documentFrequency <= UNCOMMON_MAX_DOCUMENTS ? UNCOMMON_WEIGHT : COMMON_WEIGHT;
    }

    /**
     * Splits a question into the tokens worth matching on.
     *
     * @param question the user's question
     * @return distinct lowercase tokens, stopwords and short words removed
     */
    static List<String> tokenize(String question) {
        if (question == null || question.isBlank()) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String raw : lower(question).replaceAll("[^a-z0-9\\s]", " ").split("\\s+")) {
            if (raw.length() >= MIN_TOKEN_LENGTH && !STOPWORDS.contains(raw)) {
                tokens.add(raw);
            }
        }
        return new ArrayList<>(tokens);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
