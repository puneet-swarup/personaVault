/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns entity names into file-safe, stable wiki slugs (ADR-022).
 *
 * <p>A slug is three things at once: the markdown file name, the primary key of the
 * {@code wiki_page} mirror row, and the Obsidian {@code [[wikilink]]} target. It
 * therefore has to be deterministic, lowercase, and safe on every filesystem, so
 * that recompiling the same entity always lands on the same file.
 */
public final class WikiSlugger {

    /** Longest slug we emit; keeps paths well inside filesystem limits. */
    static final int MAX_LENGTH = 80;

    /** Fallback when a name has no usable characters. */
    static final String FALLBACK = "untitled";

    private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private WikiSlugger() {
        // Utility class.
    }

    /**
     * Converts arbitrary text into a slug.
     *
     * <p>Diacritics are folded ({@code Café} becomes {@code cafe}), runs of anything
     * that is not a letter or digit collapse to a single hyphen, and the result is
     * truncated at a hyphen boundary so it never ends mid-word.
     *
     * @param input raw text, may be null or blank
     * @return a slug such as {@code hdfc-ergo-health-policy}
     */
    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return FALLBACK;
        }
        String folded = DIACRITICS
                .matcher(Normalizer.normalize(input, Normalizer.Form.NFD))
                .replaceAll("");
        String slug = NON_SLUG_CHARS
                .matcher(folded.toLowerCase(Locale.ROOT))
                .replaceAll("-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (slug.isEmpty()) {
            return FALLBACK;
        }
        return truncate(slug);
    }

    /**
     * Builds the slug for an entity page, prefixed with its type.
     *
     * <p>The type prefix keeps namespaces apart: a {@code POLICY} numbered
     * {@code 2026} and a {@code VEHICLE} numbered {@code 2026} get different pages.
     *
     * @param entityType domain type, e.g. {@code POLICY}
     * @param canonicalName normalised display name, e.g. {@code HP-2025-00123}
     * @return a slug such as {@code policy-hp-2025-00123}
     */
    public static String entitySlug(String entityType, String canonicalName) {
        String type = slugify(entityType);
        String name = slugify(canonicalName);
        String combined = type + "-" + name;
        if (combined.length() <= MAX_LENGTH) {
            return combined;
        }
        return truncate(combined);
    }

    // Cuts at the last hyphen before the limit so slugs stay readable.
    private static String truncate(String slug) {
        if (slug.length() <= MAX_LENGTH) {
            return slug;
        }
        String cut = slug.substring(0, MAX_LENGTH);
        int lastHyphen = cut.lastIndexOf('-');
        if (lastHyphen > 0) {
            cut = cut.substring(0, lastHyphen);
        }
        return cut.replaceAll("-+$", "");
    }
}
