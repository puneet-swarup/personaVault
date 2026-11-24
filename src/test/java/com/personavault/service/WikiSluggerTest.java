/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WikiSlugger")
class WikiSluggerTest {

    @Test
    @DisplayName("should lowercase and hyphenate a display name")
    void shouldLowercaseAndHyphenate() {
        assertEquals(
                "hdfc-ergo-health-policy",
                WikiSlugger.slugify("HDFC Ergo Health Policy"),
                "should lowercase and hyphenate a display name");
    }

    @Test
    @DisplayName("should collapse runs of punctuation into one hyphen and trim ends")
    void shouldCollapseRunsAndTrim() {
        assertEquals(
                "a-b",
                WikiSlugger.slugify("  a___b!! "),
                "should collapse runs of punctuation into one hyphen and trim ends");
        assertEquals(
                "a-b",
                WikiSlugger.slugify("--a--b--"),
                "should collapse runs of punctuation into one hyphen and trim ends");
    }

    @Test
    @DisplayName("should fold diacritics to ascii")
    void shouldFoldDiacritics() {
        assertEquals("cafe-creme", WikiSlugger.slugify("Café Crème"), "should fold diacritics to ascii");
    }

    @Test
    @DisplayName("should fall back to 'untitled' for blank or unusable input")
    void shouldFallbackForUnusableInput() {
        assertEquals(
                WikiSlugger.FALLBACK,
                WikiSlugger.slugify(null),
                "should fall back to 'untitled' for blank or unusable input");
        assertEquals(
                WikiSlugger.FALLBACK,
                WikiSlugger.slugify("   "),
                "should fall back to 'untitled' for blank or unusable input");
        assertEquals(
                WikiSlugger.FALLBACK,
                WikiSlugger.slugify("!!!"),
                "should fall back to 'untitled' for blank or unusable input");
    }

    @Test
    @DisplayName("should cap slug length")
    void shouldCapLength() {
        String slug = WikiSlugger.slugify("a".repeat(300));
        assertTrue(slug.length() <= WikiSlugger.MAX_LENGTH, "slug was " + slug.length() + " chars");
    }

    @Test
    @DisplayName("should truncate at a hyphen boundary so it does not end mid-word")
    void shouldTruncateAtHyphenBoundary() {
        String slug = WikiSlugger.slugify("alpha ".repeat(30));
        assertTrue(
                slug.length() <= WikiSlugger.MAX_LENGTH,
                "should truncate at a hyphen boundary so it does not end mid-word");
        assertTrue(!slug.endsWith("-"), "slug should not end with a hyphen: " + slug);
    }

    @Test
    @DisplayName("should prefix the entity type when building an entity slug")
    void shouldBuildEntitySlug() {
        assertEquals(
                "policy-hp-2025-00123",
                WikiSlugger.entitySlug("POLICY", "HP-2025-00123"),
                "should prefix the entity type when building an entity slug");
    }

    @Test
    @DisplayName("should keep same-named entities of different types apart")
    void shouldPrefixTypeToAvoidCollisions() {
        assertNotEquals(
                WikiSlugger.entitySlug("POLICY", "2026"),
                WikiSlugger.entitySlug("VEHICLE", "2026"),
                "should keep same-named entities of different types apart");
    }
}
