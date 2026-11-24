/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.util.Objects;

/**
 * A compiled wiki page loaded for answering (Tier 3).
 *
 * @param slug the page slug, which is also its file name and wikilink target
 * @param title human-readable page title
 * @param markdown the page body, read from disk where possible (disk is canonical)
 */
public record WikiPageContext(String slug, String title, String markdown) {

    /**
     * Rejects nulls: a page with no body must never reach the prompt builder.
     */
    public WikiPageContext {
        Objects.requireNonNull(slug, "slug is required");
        Objects.requireNonNull(title, "title is required");
        Objects.requireNonNull(markdown, "markdown is required");
    }
}
