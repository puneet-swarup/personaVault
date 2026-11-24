/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.personavault.entity.WikiPageType;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WikiIndexBuilder")
class WikiIndexBuilderTest {

    private static final Instant COMPILED_AT = Instant.parse("2026-11-24T10:15:30Z");

    private final WikiIndexBuilder builder = new WikiIndexBuilder();

    @Test
    @DisplayName("should group entries by page type and sort them by title")
    void shouldGroupAndSort() {
        String markdown = builder.render(
                List.of(
                        new WikiIndexEntry("policy-b", WikiPageType.ENTITY, "Bravo policy"),
                        new WikiIndexEntry("policy-a", WikiPageType.ENTITY, "Alpha policy"),
                        new WikiIndexEntry("topic-insurance", WikiPageType.TOPIC, "Insurance overview")),
                COMPILED_AT);

        assertTrue(markdown.contains("## Entity"), markdown);
        assertTrue(markdown.contains("## Topic"), markdown);
        int alpha = markdown.indexOf("[[policy-a|Alpha policy]]");
        int bravo = markdown.indexOf("[[policy-b|Bravo policy]]");
        assertTrue(alpha > 0 && bravo > alpha, "entity entries should be title-sorted: " + markdown);
        assertTrue(markdown.contains("_3 pages._"), markdown);
    }

    @Test
    @DisplayName("should never list the index page itself")
    void shouldExcludeIndexEntries() {
        String markdown = builder.render(
                List.of(
                        new WikiIndexEntry("index", WikiPageType.INDEX, "Wiki Index"),
                        new WikiIndexEntry("policy-a", WikiPageType.ENTITY, "Alpha policy")),
                COMPILED_AT);
        assertTrue(markdown.contains("_1 page._"), markdown);
        assertTrue(!markdown.contains("[[index|"), markdown);
    }

    @Test
    @DisplayName("should explain an empty wiki instead of rendering an empty list")
    void shouldRenderEmptyState() {
        String markdown = builder.render(List.of(), COMPILED_AT);
        assertTrue(markdown.contains("_Nothing compiled yet."), markdown);
        assertTrue(markdown.contains("_0 pages._"), markdown);
    }

    @Test
    @DisplayName("should carry frontmatter so the index is a valid page")
    void shouldRenderFrontmatter() {
        String markdown = builder.render(List.of(), COMPILED_AT);
        assertTrue(markdown.startsWith("---\nslug: index\ntype: index\n"), markdown);
        assertTrue(markdown.contains("lastCompiledAt: 2026-11-24T10:15:30Z"), "frontmatter timestamp");
    }
}
