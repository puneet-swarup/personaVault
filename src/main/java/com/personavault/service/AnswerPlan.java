/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.util.Objects;
import java.util.Optional;

/**
 * What the chat path should do with one question: which tier, and the page if any.
 *
 * @param decision the router's verdict
 * @param page the compiled page to answer from, or null when the answer comes from search
 */
public record AnswerPlan(RoutingDecision decision, WikiPageContext page) {

    /**
     * Rejects a missing decision; the page is optional by design.
     */
    public AnswerPlan {
        Objects.requireNonNull(decision, "decision is required");
    }

    /**
     * The page to answer from, when the wiki tier was selected.
     *
     * @return the page, or empty when the plan escalates to document search
     */
    public Optional<WikiPageContext> wikiPage() {
        return Optional.ofNullable(page);
    }

    /**
     * True when this question is answered from a compiled page rather than from search.
     *
     * @return true when a wiki page is attached
     */
    public boolean answersFromWiki() {
        return page != null;
    }
}
