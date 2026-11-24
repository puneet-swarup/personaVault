/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.personavault.entity.WikiPage;
import com.personavault.entity.WikiPageType;
import com.personavault.repository.WikiPageRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WikiQueryService")
class WikiQueryServiceTest {

    private static final String POLICY_BODY = """
            ---
            slug: policy-hp-2025-00123
            type: entity
            ---
            # HP-2025-00123

            ## Facts

            | Field | Value | Confidence |
            | --- | --- | --- |
            | Annual premium | INR 1,20,000 [^f:44] | 95% |
            """;

    @Mock
    private WikiPageRepository wikiPageRepository;

    @TempDir
    Path tempDir;

    private WikiFileStore fileStore;
    private WikiQueryService service;

    @BeforeEach
    void setUp() {
        fileStore = new WikiFileStore(tempDir);
        service = new WikiQueryService(wikiPageRepository, fileStore);
    }

    private static WikiPage page(String slug, WikiPageType type, String title, String body) {
        WikiPage page = new WikiPage();
        page.setSlug(slug);
        page.setPageType(type);
        page.setTitle(title);
        page.setBodyMarkdown(body);
        page.setVersion(1);
        return page;
    }

    private void givenPages(WikiPage... pages) {
        when(wikiPageRepository.findAllByOrderByPageTypeAscTitleAsc()).thenReturn(List.of(pages));
    }

    @Test
    @DisplayName("should return the page whose title matches the question")
    void shouldMatchByTitle() {
        WikiPage policy = page("policy-hp-2025-00123", WikiPageType.ENTITY, "HP-2025-00123", POLICY_BODY);
        WikiPage insurer = page("insurer-hdfc-ergo", WikiPageType.ENTITY, "HDFC Ergo", "---\nslug: insurer\n---\n");
        givenPages(policy, insurer);

        Optional<WikiPageContext> found = service.findPage("When does HP-2025-00123 expire?");

        assertTrue(found.isPresent(), "expected a page for the policy number");
        assertEquals("policy-hp-2025-00123", found.get().slug(), "wrong page selected");
    }

    @Test
    @DisplayName("should read the page body from disk, not from the mirror")
    void shouldPreferFileOnDisk() {
        WikiPage policy = page("policy-hp-2025-00123", WikiPageType.ENTITY, "HP-2025-00123", "stale mirrored body");
        givenPages(policy);
        fileStore.write(WikiPageType.ENTITY, "policy-hp-2025-00123", POLICY_BODY);

        WikiPageContext context = service.findPage("HP-2025-00123 premium").orElseThrow();

        assertTrue(
                context.markdown().contains("INR 1,20,000"), "expected the file content, got: " + context.markdown());
        assertFalse(context.markdown().contains("stale"), "mirror body must not win over the file");
    }

    @Test
    @DisplayName("should fall back to the mirrored body when the file is missing")
    void shouldFallBackToMirroredBody() {
        WikiPage policy = page("policy-hp-2025-00123", WikiPageType.ENTITY, "HP-2025-00123", POLICY_BODY);
        givenPages(policy);

        WikiPageContext context = service.findPage("HP-2025-00123").orElseThrow();

        assertTrue(context.markdown().contains("Annual premium"), "expected the mirrored body as a fallback");
    }

    @Test
    @DisplayName("should prefer a rare token over one that appears on every page")
    void shouldWeightRareTokensHigher() {
        WikiPage health = page("policy-health-001", WikiPageType.ENTITY, "Health policy 001", "insurer: HDFC Ergo");
        WikiPage car = page("policy-car-002", WikiPageType.ENTITY, "Car policy 002", "insurer: ICICI Lombard");
        givenPages(health, car);

        WikiPageContext context = service.findPage("car policy details").orElseThrow();

        assertEquals("policy-car-002", context.slug(), "the distinctive token should win");
    }

    @Test
    @DisplayName("should never answer from the index page")
    void shouldSkipIndexPage() {
        WikiPage index = page("index", WikiPageType.INDEX, "Wiki Index", "policy policy policy");
        givenPages(index);

        assertTrue(service.findPage("policy index").isEmpty(), "the index must not answer questions");
    }

    @Test
    @DisplayName("should return empty when the vault has no pages")
    void shouldHandleEmptyVault() {
        givenPages();

        assertTrue(service.findPage("when does my policy expire").isEmpty(), "empty vault must not match");
    }

    @Test
    @DisplayName("should return empty when no token matches any page")
    void shouldReturnEmptyWhenNothingMatches() {
        WikiPage policy = page("policy-hp-2025-00123", WikiPageType.ENTITY, "HP-2025-00123", POLICY_BODY);
        givenPages(policy);

        assertTrue(service.findPage("capital of france").isEmpty(), "unrelated questions must not match");
    }

    @Test
    @DisplayName("should return empty for a question made only of stopwords")
    void shouldReturnEmptyForStopwordOnlyQuestion() {
        assertTrue(service.findPage("what is the").isEmpty(), "stopwords alone cannot identify a page");
        assertTrue(service.findPage("   ").isEmpty(), "blank question cannot identify a page");
        assertTrue(service.findPage(null).isEmpty(), "null question cannot identify a page");
    }

    @Test
    @DisplayName("should tokenize away stopwords and short words")
    void shouldTokenize() {
        assertEquals(
                List.of("premium", "hdfc", "ergo"),
                WikiQueryService.tokenize("What is the premium for HDFC Ergo?"),
                "unexpected tokens");
        assertTrue(WikiQueryService.tokenize("a b c").isEmpty(), "short words should be dropped");
    }
}
