/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

/**
 * Thrown when a compiled wiki page breaks the provenance contract (ADR-022).
 *
 * <p>A page that states a value without citing the fact it came from is not written
 * to disk: an uncited claim is exactly the failure mode the vault exists to prevent.
 */
public class WikiPageValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message description of the broken rules, one per line
     */
    public WikiPageValidationException(String message) {
        super(message);
    }
}
