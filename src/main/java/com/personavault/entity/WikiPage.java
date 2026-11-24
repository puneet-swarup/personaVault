/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

// The database mirror of one markdown file under ./data/wiki/ (ADR-022).
// Disk is canonical: this row exists so pages can be listed and searched without
// walking the filesystem. When the two disagree the file wins and this row is
// rebuilt on the next compile. The primary key is the slug, not a generated id,
// because the slug is also the file name and the Obsidian [[wikilink]] target.
@Getter
@Setter
@Entity
@Table(name = "wiki_page")
public class WikiPage {

    @Id
    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "page_type", nullable = false, length = 20)
    private WikiPageType pageType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body_markdown", nullable = false)
    private String bodyMarkdown;

    // Set for ENTITY pages, null for topic and index pages.
    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    // Incremented on every recompile of this page (ADR-022 frontmatter version).
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "last_compiled_at", nullable = false)
    private Instant lastCompiledAt;
}
