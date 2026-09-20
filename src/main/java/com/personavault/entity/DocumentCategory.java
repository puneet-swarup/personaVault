/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.entity;

/**
 * Classification of a document by domain.
 *
 * <p>Used for:
 * <ul>
 *   <li>RAG narrowing: chat queries filter vector search by category</li>
 *   <li>Dashboard grouping: "Health policies", "Financial docs", etc.</li>
 *   <li>Document list filtering in the UI</li>
 * </ul>
 *
 * <p>Stored as a VARCHAR(50) in the database. The enum provides type safety
 * in Java while the DB remains flexible (new categories can be added via
 * migration without code changes if needed).
 */
public enum DocumentCategory {
    HEALTH_INSURANCE("Health Insurance"),
    CAR_INSURANCE("Car Insurance"),
    LIFE_INSURANCE("Life Insurance"),
    HOME_INSURANCE("Home Insurance"),
    TRAVEL_INSURANCE("Travel Insurance"),
    FINANCIAL("Financial"),
    MEDICAL("Medical"),
    LEGAL("Legal"),
    IDENTITY("Identity"),
    EMPLOYMENT("Employment"),
    EDUCATION("Education"),
    REAL_ESTATE("Real Estate"),
    FAMILY("Family"),
    VEHICLE("Vehicle"),
    OTHER("Other");

    private final String displayName;

    DocumentCategory(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable name for UI display. */
    public String getDisplayName() {
        return displayName;
    }
}
