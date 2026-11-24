/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.personavault.entity.Fact;
import com.personavault.entity.WikiPageType;

/**
 * Everything the markdown renderer needs to draw one page, and nothing else.
 *
 * <p>Keeping this a plain value object (no repositories, no clock, no IO) is what
 * makes the renderer a pure function: the same model always produces byte-identical
 * markdown, which is what the provenance validator and the tests rely on.
 *
 * @param slug file name and wikilink target
 * @param pageType which subfolder the page belongs in
 * @param title heading text for the page
 * @param entityRef {@code TYPE:NAME} reference written into frontmatter, or null
 * @param needsReview true when the page carries values the vault does not trust yet
 * @param version incremented on each recompile
 * @param compiledAt timestamp written into frontmatter
 * @param facts every fact known about the entity, current and historical
 * @param sourceNames document id to original file name, for footnote text
 * @param entityLinks entity id to the page that describes it, for wikilinks
 */
public record WikiRenderModel(
        String slug,
        WikiPageType pageType,
        String title,
        String entityRef,
        boolean needsReview,
        int version,
        Instant compiledAt,
        List<Fact> facts,
        Map<Long, String> sourceNames,
        Map<Long, WikiLink> entityLinks) {

    /**
     * Copies the collections so a caller cannot mutate a model after construction.
     */
    public WikiRenderModel {
        facts = List.copyOf(facts);
        sourceNames = Map.copyOf(sourceNames);
        entityLinks = Map.copyOf(entityLinks);
    }

    /**
     * A resolved wikilink target.
     *
     * @param slug the page slug to link to
     * @param label human-readable name of the linked thing
     */
    public record WikiLink(String slug, String label) {}
}
