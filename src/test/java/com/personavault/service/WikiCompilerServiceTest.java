/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.personavault.entity.Document;
import com.personavault.entity.Fact;
import com.personavault.entity.VaultEntity;
import com.personavault.entity.WikiPage;
import com.personavault.repository.DocumentRepository;
import com.personavault.repository.FactRepository;
import com.personavault.repository.VaultEntityRepository;
import com.personavault.repository.WikiPageRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WikiCompilerService")
class WikiCompilerServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T10:15:30Z"), ZoneOffset.UTC);

    @Mock
    private VaultEntityRepository entityRepository;

    @Mock
    private FactRepository factRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private WikiPageRepository wikiPageRepository;

    @TempDir
    Path tempDir;

    private WikiCompilerService service;

    @BeforeEach
    void setUp() {
        service = new WikiCompilerService(
                entityRepository,
                factRepository,
                documentRepository,
                wikiPageRepository,
                new WikiMarkdownRenderer(),
                new WikiPageValidator(),
                new WikiIndexBuilder(),
                new WikiFileStore(tempDir),
                CLOCK);
    }

    private static VaultEntity entity(long id, String type, String name, Instant deletedAt) {
        VaultEntity entity = new VaultEntity();
        ReflectionTestUtils.setField(entity, "id", id);
        entity.setEntityType(type);
        entity.setCanonicalName(name);
        entity.setDeletedAt(deletedAt);
        return entity;
    }

    private static Fact fact(long id, long entityId, String key, String valueType) {
        Fact fact = new Fact();
        ReflectionTestUtils.setField(fact, "id", id);
        fact.setEntityId(entityId);
        fact.setFactKey(key);
        fact.setValueType(valueType);
        fact.setSourceDocId(3L);
        fact.setConfidence(new BigDecimal("0.95"));
        fact.setVerificationStatus("EXTRACTED");
        fact.setExtractedBy("LLM");
        fact.setFirstSeenAt(Instant.parse("2026-11-20T00:00:00Z"));
        return fact;
    }

    private static Document document(long id, String fileName) {
        Document document = new Document();
        ReflectionTestUtils.setField(document, "id", id);
        document.setFileName(fileName);
        return document;
    }

    @Test
    @DisplayName("should write the markdown file and the mirror row")
    void shouldCompileEntity() {
        Fact expiry = fact(2L, 1L, "expiryDate", "DATE");
        expiry.setValueDate(LocalDate.of(2026, 3, 14));

        when(entityRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "POLICY", "HP-1", null)));
        when(factRepository.findByEntityIdOrderByFactKeyAscValidFromAsc(1L)).thenReturn(List.of(expiry));
        when(documentRepository.findAllById(any())).thenReturn(List.of(document(3L, "policy_hdfc.pdf")));
        when(wikiPageRepository.findById("policy-hp-1")).thenReturn(Optional.empty());
        when(wikiPageRepository.save(any(WikiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wikiPageRepository.findAllByOrderByPageTypeAscTitleAsc()).thenReturn(List.of());

        Optional<WikiPage> compiled = service.compileEntity(1L);

        assertTrue(compiled.isPresent(), "should write the markdown file and the mirror row");
        assertEquals("policy-hp-1", compiled.get().getSlug(), "should write the markdown file and the mirror row");
        assertEquals(1, compiled.get().getVersion(), "should write the markdown file and the mirror row");
        assertFalse(compiled.get().isNeedsReview(), "should write the markdown file and the mirror row");
        assertEquals(1L, compiled.get().getEntityId(), "should write the markdown file and the mirror row");

        Path page = tempDir.resolve("entity").resolve("policy-hp-1.md");
        assertTrue(Files.isRegularFile(page), "page file was not written");
        String markdown = readString(page);
        assertTrue(markdown.contains("| Expiry date | 2026-03-14 [^f:2] | 95% |"), markdown);
        assertTrue(markdown.contains("[^f:2]: from policy_hdfc.pdf"), markdown);
        assertTrue(Files.isRegularFile(tempDir.resolve("index.md")), "index was not written");
    }

    @Test
    @DisplayName("should skip an entity with no facts")
    void shouldSkipEntityWithoutFacts() {
        when(entityRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "POLICY", "HP-1", null)));
        when(factRepository.findByEntityIdOrderByFactKeyAscValidFromAsc(1L)).thenReturn(List.of());

        assertTrue(service.compileEntity(1L).isEmpty(), "should skip an entity with no facts");
        verify(wikiPageRepository, never()).save(any());
    }

    @Test
    @DisplayName("should skip an unknown or deleted entity")
    void shouldSkipDeletedEntity() {
        when(entityRepository.findById(1L)).thenReturn(Optional.empty());
        when(entityRepository.findById(2L))
                .thenReturn(Optional.of(entity(2L, "POLICY", "HP-2", Instant.parse("2026-11-01T00:00:00Z"))));

        assertTrue(service.compileEntity(1L).isEmpty(), "should skip an unknown or deleted entity");
        assertTrue(service.compileEntity(2L).isEmpty(), "should skip an unknown or deleted entity");
    }

    @Test
    @DisplayName("should bump the version of an already compiled page")
    void shouldIncrementVersion() {
        Fact expiry = fact(2L, 1L, "expiryDate", "DATE");
        expiry.setValueDate(LocalDate.of(2026, 3, 14));
        WikiPage existing = new WikiPage();
        existing.setSlug("policy-hp-1");
        existing.setVersion(3);

        when(entityRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "POLICY", "HP-1", null)));
        when(factRepository.findByEntityIdOrderByFactKeyAscValidFromAsc(1L)).thenReturn(List.of(expiry));
        when(documentRepository.findAllById(any())).thenReturn(List.of(document(3L, "policy_hdfc.pdf")));
        when(wikiPageRepository.findById("policy-hp-1")).thenReturn(Optional.of(existing));
        when(wikiPageRepository.save(any(WikiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wikiPageRepository.findAllByOrderByPageTypeAscTitleAsc()).thenReturn(List.of(existing));

        WikiPage compiled = service.compileEntity(1L).orElseThrow();

        assertEquals(4, compiled.getVersion(), "should bump the version of an already compiled page");
        assertTrue(
                readString(tempDir.resolve("entity").resolve("policy-hp-1.md")).contains("version: 4"),
                "should bump the version of an already compiled page");
    }

    @Test
    @DisplayName("should flag a page for review when a fact is low confidence")
    void shouldFlagLowConfidence() {
        Fact weak = fact(2L, 1L, "expiryDate", "DATE");
        weak.setValueDate(LocalDate.of(2026, 3, 14));
        weak.setConfidence(new BigDecimal("0.40"));

        when(entityRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "POLICY", "HP-1", null)));
        when(factRepository.findByEntityIdOrderByFactKeyAscValidFromAsc(1L)).thenReturn(List.of(weak));
        when(documentRepository.findAllById(any())).thenReturn(List.of(document(3L, "policy_hdfc.pdf")));
        when(wikiPageRepository.findById("policy-hp-1")).thenReturn(Optional.empty());
        when(wikiPageRepository.save(any(WikiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wikiPageRepository.findAllByOrderByPageTypeAscTitleAsc()).thenReturn(List.of());

        WikiPage compiled = service.compileEntity(1L).orElseThrow();

        assertTrue(compiled.isNeedsReview(), "should flag a page for review when a fact is low confidence");
        assertTrue(
                readString(tempDir.resolve("entity").resolve("policy-hp-1.md")).contains("needsReview: true"),
                "should flag a page for review when a fact is low confidence");
    }

    @Test
    @DisplayName("should compile each page touched by a document exactly once")
    void shouldCompilePagesForDocument() {
        Fact first = fact(2L, 1L, "expiryDate", "DATE");
        first.setValueDate(LocalDate.of(2026, 3, 14));
        Fact second = fact(3L, 1L, "insurerName", "STRING");
        second.setValueText("HDFC Ergo");

        when(factRepository.findBySourceDocId(3L)).thenReturn(List.of(first, second));
        when(entityRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "POLICY", "HP-1", null)));
        when(factRepository.findByEntityIdOrderByFactKeyAscValidFromAsc(1L)).thenReturn(List.of(first, second));
        when(documentRepository.findAllById(any())).thenReturn(List.of(document(3L, "policy_hdfc.pdf")));
        when(wikiPageRepository.findById("policy-hp-1")).thenReturn(Optional.empty());
        when(wikiPageRepository.save(any(WikiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wikiPageRepository.findAllByOrderByPageTypeAscTitleAsc()).thenReturn(List.of());

        assertEquals(1, service.compileForDocument(3L), "should compile each page touched by a document exactly once");
        verify(entityRepository).findById(1L);
    }

    @Test
    @DisplayName("should return zero for a document with no facts")
    void shouldHandleDocumentWithoutFacts() {
        when(factRepository.findBySourceDocId(anyLong())).thenReturn(List.of());

        assertEquals(0, service.compileForDocument(99L), "should return zero for a document with no facts");
    }

    @Test
    @DisplayName("should recompile every live entity and refresh the index")
    void shouldCompileAll() {
        Fact expiry = fact(2L, 1L, "expiryDate", "DATE");
        expiry.setValueDate(LocalDate.of(2026, 3, 14));

        when(entityRepository.findAll())
                .thenReturn(List.of(
                        entity(1L, "POLICY", "HP-1", null),
                        entity(2L, "POLICY", "HP-2", Instant.parse("2026-11-01T00:00:00Z"))));
        when(entityRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "POLICY", "HP-1", null)));
        when(factRepository.findByEntityIdOrderByFactKeyAscValidFromAsc(1L)).thenReturn(List.of(expiry));
        when(documentRepository.findAllById(any())).thenReturn(List.of(document(3L, "policy_hdfc.pdf")));
        when(wikiPageRepository.findById("policy-hp-1")).thenReturn(Optional.empty());
        when(wikiPageRepository.save(any(WikiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(wikiPageRepository.findAllByOrderByPageTypeAscTitleAsc()).thenReturn(List.of());

        assertEquals(1, service.compileAll(), "should recompile every live entity and refresh the index");
        assertTrue(
                Files.isRegularFile(tempDir.resolve("index.md")),
                "should recompile every live entity and refresh the index");
    }

    @Test
    @DisplayName("should resolve entity references into wikilinks")
    void shouldLinkReferencedEntities() {
        Fact insurer = fact(4L, 1L, "insurer", "ENTITY_REF");
        insurer.setValueEntityId(9L);

        when(entityRepository.findById(1L)).thenReturn(Optional.of(entity(1L, "POLICY", "HP-1", null)));
        when(factRepository.findByEntityIdOrderByFactKeyAscValidFromAsc(1L)).thenReturn(List.of(insurer));
        when(documentRepository.findAllById(any())).thenReturn(List.of(document(3L, "policy_hdfc.pdf")));
        when(entityRepository.findAllById(any())).thenReturn(List.of(entity(9L, "INSURER", "HDFC Ergo", null)));
        when(wikiPageRepository.findById("policy-hp-1")).thenReturn(Optional.empty());
        when(wikiPageRepository.save(any(WikiPage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(wikiPageRepository.findAllByOrderByPageTypeAscTitleAsc()).thenReturn(List.of());

        service.compileEntity(1L);

        String markdown = readString(tempDir.resolve("entity").resolve("policy-hp-1.md"));
        assertTrue(markdown.contains("[[insurer-hdfc-ergo|HDFC Ergo]]"), markdown);
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
