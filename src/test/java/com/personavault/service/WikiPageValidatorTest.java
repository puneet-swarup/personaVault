/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WikiPageValidator")
class WikiPageValidatorTest {

    private final WikiPageValidator validator = new WikiPageValidator();

    private static String page(String body) {
        return "---\nslug: policy-hp-1\ntype: entity\nversion: 1\n---\n" + body;
    }

    @Test
    @DisplayName("should accept a page whose every claim carries a citation")
    void shouldAcceptCitedPage() {
        String markdown = page("""
                # HP-1

                ## Facts

                | Field | Value | Confidence |
                | --- | --- | --- |
                | Expiry date | 2026-03-14 [^f:2] | 95% |

                ## Sources

                [^f:2]: from policy.pdf, page 1
                """);
        assertTrue(
                validator.findViolations(markdown).isEmpty(),
                "should accept a page whose every claim carries a citation");
    }

    @Test
    @DisplayName("should reject a table row with no citation")
    void shouldRejectUncitedTableRow() {
        String markdown = page("""
                # HP-1

                ## Facts

                | Field | Value | Confidence |
                | --- | --- | --- |
                | Expiry date | 2026-03-14 | 95% |
                """);
        List<String> violations = validator.findViolations(markdown);
        assertFalse(violations.isEmpty(), "should reject a table row with no citation");
        assertTrue(violations.getFirst().contains("claim without"), violations.getFirst());
    }

    @Test
    @DisplayName("should reject a history bullet with no citation")
    void shouldRejectUncitedHistoryBullet() {
        String markdown = page("""
                ## History

                - Annual premium: INR 5,00,000 (2024-04-01 to 2025-03-31)
                """);
        assertFalse(validator.findViolations(markdown).isEmpty(), "should reject a history bullet with no citation");
    }

    @Test
    @DisplayName("should not treat footnote definitions as claims")
    void shouldIgnoreFootnoteDefinitions() {
        String markdown = page("""
                ## Sources

                [^f:2]: from policy.pdf
                [^d:9]: from bank statement.pdf
                """);
        assertTrue(validator.findViolations(markdown).isEmpty(), "should not treat footnote definitions as claims");
    }

    @Test
    @DisplayName("should not treat link bullets as claims")
    void shouldIgnoreLinkBullets() {
        String markdown = page("""
                ## Links

                - Insurer: [[insurer-hdfc-ergo|HDFC Ergo]]
                """);
        assertTrue(validator.findViolations(markdown).isEmpty(), "should not treat link bullets as claims");
    }

    @Test
    @DisplayName("should ignore table rows inside a fenced code block")
    void shouldIgnoreCodeFence() {
        String markdown = page("""
                ## Example

                ```
                | Field | Value |
                | --- | --- |
                | raw | uncited |
                ```
                """);
        assertTrue(validator.findViolations(markdown).isEmpty(), "should ignore table rows inside a fenced code block");
    }

    @Test
    @DisplayName("should reject a page with no frontmatter")
    void shouldRejectMissingFrontmatter() {
        List<String> violations = validator.findViolations("# HP-1\n\nNo frontmatter here.\n");
        assertFalse(violations.isEmpty(), "should reject a page with no frontmatter");
        assertTrue(violations.getFirst().contains("frontmatter"), violations.getFirst());
    }

    @Test
    @DisplayName("should reject an empty page")
    void shouldRejectEmptyPage() {
        assertFalse(validator.findViolations("").isEmpty(), "should reject an empty page");
        assertFalse(validator.findViolations(null).isEmpty(), "should reject an empty page");
    }

    @Test
    @DisplayName("should throw when validating an uncited page")
    void shouldThrowOnUncitedPage() {
        String markdown = page("""
                ## Facts

                | Field | Value | Confidence |
                | --- | --- | --- |
                | Expiry date | 2026-03-14 | 95% |
                """);
        WikiPageValidationException error =
                org.junit.jupiter.api.Assertions.assertThrows(WikiPageValidationException.class, () -> {
                    validator.validate(markdown);
                });
        assertTrue(error.getMessage().contains("ADR-022"), error.getMessage());
    }
}
