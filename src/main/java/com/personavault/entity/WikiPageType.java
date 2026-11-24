/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.entity;

/**
 * Kind of wiki page, which also decides the subfolder the page is written to.
 *
 * <p>Per ADR-022 the folder layout under {@code ./data/wiki/} mirrors these types so
 * the directory opens as a tidy Obsidian vault.
 */
public enum WikiPageType {

    /** One page per vault entity (policy, insurer, provider, ...). */
    ENTITY("entity"),

    /** A hand-written or compiled page that spans entities. */
    TOPIC("topic"),

    /** A chronological page over one entity's facts. */
    TIMELINE("timeline"),

    /** The generated table of contents at the wiki root. */
    INDEX("index");

    private final String folderName;

    WikiPageType(String folderName) {
        this.folderName = folderName;
    }

    /**
     * Returns the subfolder name for this page type.
     *
     * @return folder name, e.g. {@code entity}
     */
    public String folder() {
        return folderName;
    }
}
