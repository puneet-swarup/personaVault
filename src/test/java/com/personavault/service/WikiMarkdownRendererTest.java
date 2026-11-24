/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.personavault.entity.Fact;
import com.personavault.entity.WikiPageType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WikiMarkdownRenderer")
class WikiMarkdownRendererTest {

    private static final Instant COMPILED_AT = Instant.parse("2026-11-24T10:15:30Z");
    private static final long DOC_ID = 3L;

    private final WikiMarkdownRenderer renderer = new WikiMarkdownRenderer();

    private static Fact fact(long id, String key, String valueType) {
        Fact fact = new Fact();
        ReflectionTestUtils.setField(fact, "id", id);
        fact.setEntityId(1L);
        fact.setFactKey(key);
        fact.setValueType(valueType);
        fact.setSourceDocId(DOC_ID);
        fact.setConfidence(new BigDecimal("0.95"));
        fact.setVerificationStatus("EXTRACTED");
        fact.setExtractedBy("LLM");
        fact.setFirstSeenAt(Instant.parse("2026-11-20T00:00:00Z"));
        return fact;
    }

    private String render(List<Fact> facts, Map<Long, WikiRenderModel.WikiLink> links, boolean needsReview) {
        WikiRenderModel model = new WikiRenderModel(
                "policy-hp-1",
                WikiPageType.ENTITY,
                "HP-1",
                "POLICY:HP-1",
                needsReview,
                2,
                COMPILED_AT,
                facts,
                Map.of(DOC_ID, "policy_hdfc.pdf"),
                links);
        return renderer.render(model);
    }

    @Test
    @DisplayName("should write YAML frontmatter with slug, type, version and refs")
    void shouldRenderFrontmatter() {
        Fact expiry = fact(2L, "expiryDate", "DATE");
        expiry.setValueDate(LocalDate.of(2026, 3, 14));

        String markdown = render(List.of(expiry), Map.of(), false);

        assertTrue(markdown.startsWith("---\n"), markdown);
        assertTrue(markdown.contains("slug: policy-hp-1"), markdown);
        assertTrue(markdown.contains("type: entity"), markdown);
        assertTrue(markdown.contains("entity: \"POLICY:HP-1\""), markdown);
        assertTrue(markdown.contains("lastCompiledAt: 2026-11-24T10:15:30Z"), markdown);
        assertTrue(markdown.contains("needsReview: false"), markdown);
        assertTrue(markdown.contains("version: 2"), markdown);
        assertTrue(markdown.contains("sourceRefs: [doc:3]"), markdown);
        assertTrue(markdown.contains("factRefs: [fact:2]"), markdown);
        assertTrue(markdown.contains("# HP-1"), markdown);
    }

    @Test
    @DisplayName("should render a dated fact as a cited table row with confidence")
    void shouldRenderDateFactRow() {
        Fact expiry = fact(2L, "expiryDate", "DATE");
        expiry.setValueDate(LocalDate.of(2026, 3, 14));

        String markdown = render(List.of(expiry), Map.of(), false);

        assertTrue(markdown.contains("| Expiry date | 2026-03-14 [^f:2] | 95% |"), markdown);
        assertTrue(markdown.contains("[^f:2]: from policy_hdfc.pdf"), markdown);
    }

    @Test
    @DisplayName("should format money with Indian digit grouping and currency")
    void shouldFormatMoney() {
        Fact premium = fact(5L, "annualPremium", "MONEY");
        premium.setValueNum(new BigDecimal("120000"));
        premium.setCurrency("INR");

        String markdown = render(List.of(premium), Map.of(), false);

        assertTrue(markdown.contains("INR 1,20,000"), markdown);
    }

    @Test
    @DisplayName("should include the source page number in the footnote when known")
    void shouldIncludeSourcePage() {
        Fact expiry = fact(2L, "expiryDate", "DATE");
        expiry.setValueDate(LocalDate.of(2026, 3, 14));
        expiry.setSourcePage(4);

        String markdown = render(List.of(expiry), Map.of(), false);

        assertTrue(markdown.contains("[^f:2]: from policy_hdfc.pdf, page 4"), markdown);
    }

    @Test
    @DisplayName("should move superseded facts into a History section with their validity period")
    void shouldRenderHistoryForSupersededFact() {
        Fact current = fact(42L, "annualPremium", "MONEY");
        current.setValueNum(new BigDecimal("120000"));
        current.setCurrency("INR");
        current.setValidFrom(LocalDate.of(2026, 4, 1));

        Fact older = fact(10L, "annualPremium", "MONEY");
        older.setValueNum(new BigDecimal("500000"));
        older.setCurrency("INR");
        older.setValidFrom(LocalDate.of(2024, 4, 1));
        older.setValidTo(LocalDate.of(2025, 3, 31));

        String markdown = render(List.of(current, older), Map.of(), false);

        assertTrue(markdown.contains("## History"), markdown);
        assertTrue(markdown.contains("- Annual premium: INR 5,00,000 (2024-04-01 to 2025-03-31) [^f:10]"), markdown);
        assertTrue(markdown.contains("| Annual premium | INR 1,20,000 [^f:42] | 95% |"), markdown);
    }

    @Test
    @DisplayName("should render an entity reference as a wikilink")
    void shouldRenderEntityReferenceAsWikilink() {
        Fact insurer = fact(7L, "insurer", "ENTITY_REF");
        insurer.setValueEntityId(9L);

        String markdown = render(
                List.of(insurer), Map.of(9L, new WikiRenderModel.WikiLink("insurer-hdfc-ergo", "HDFC Ergo")), false);

        assertTrue(markdown.contains("[[insurer-hdfc-ergo|HDFC Ergo]]"), markdown);
        assertTrue(markdown.contains("## Links"), markdown);
    }

    @Test
    @DisplayName("should fall back to an id reference when the target page is unknown")
    void shouldFallBackToIdReference() {
        Fact insurer = fact(7L, "insurer", "ENTITY_REF");
        insurer.setValueEntityId(9L);

        String markdown = render(List.of(insurer), Map.of(), false);

        assertTrue(markdown.contains("entity #9"), markdown);
    }

    @Test
    @DisplayName("should say so when an entity has no facts yet")
    void shouldRenderEmptyFactsNotice() {
        String markdown = render(List.of(), Map.of(), false);

        assertTrue(markdown.contains("_No facts recorded yet._"), markdown);
        assertFalse(markdown.contains("## Sources"), markdown);
    }

    @Test
    @DisplayName("should escape pipes in values so the table does not break")
    void shouldEscapePipes() {
        Fact note = fact(11L, "note", "STRING");
        note.setValueText("a | b");

        String markdown = render(List.of(note), Map.of(), false);

        assertTrue(markdown.contains("a \\| b"), markdown);
    }

    @Test
    @DisplayName("should flag review when a current fact is below the confidence threshold")
    void shouldFlagLowConfidenceForReview() {
        Fact weak = fact(12L, "expiryDate", "DATE");
        weak.setValueDate(LocalDate.of(2026, 3, 14));
        weak.setConfidence(new BigDecimal("0.40"));

        Fact strong = fact(13L, "insurerName", "STRING");
        strong.setValueText("HDFC Ergo");

        assertTrue(
                WikiMarkdownRenderer.needsReview(List.of(weak)),
                "should flag review when a current fact is below the confidence threshold");
        assertFalse(
                WikiMarkdownRenderer.needsReview(List.of(strong)),
                "should flag review when a current fact is below the confidence threshold");
    }

    @Test
    @DisplayName("should humanize camelCase fact keys")
    void shouldHumanizeKeys() {
        assertEquals("Expiry date", WikiMarkdownRenderer.humanize("expiryDate"), "should humanize camelCase fact keys");
        assertEquals(
                "Policy number", WikiMarkdownRenderer.humanize("policyNumber"), "should humanize camelCase fact keys");
        assertEquals("Value", WikiMarkdownRenderer.humanize(null), "should humanize camelCase fact keys");
    }

    @Test
    @DisplayName("should be deterministic across repeated renders")
    void shouldBeDeterministic() {
        Fact a = fact(2L, "expiryDate", "DATE");
        a.setValueDate(LocalDate.of(2026, 3, 14));
        Fact b = fact(1L, "insurerName", "STRING");
        b.setValueText("HDFC Ergo");

        assertEquals(
                render(List.of(a, b), Map.of(), false),
                render(List.of(b, a), Map.of(), false),
                "should be deterministic across repeated renders");
    }
}
