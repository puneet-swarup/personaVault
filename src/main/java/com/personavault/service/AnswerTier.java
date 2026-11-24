/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

/**
 * Which tier of the truth model answers a question (ADR-016, ADR-023).
 *
 * <p>The ladder is walked from the most authoritative tier down:
 * exact facts, then the compiled wiki page, then document search. RAG is the last
 * resort, not the default.
 */
public enum AnswerTier {

    /** Tier 2: an exact stored value. */
    FACT,

    /** Tier 3: a compiled wiki page. */
    WIKI,

    /** Tier 1: vector search over source documents. */
    RAG
}
