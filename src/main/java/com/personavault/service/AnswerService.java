/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Turns a question into an {@link AnswerPlan}: route it, then attach the page it hits.
 *
 * <p>Implements the escalation ladder from ADR-016 as a single, testable decision, so the
 * controller does no thinking of its own:
 * <ol>
 *   <li>An exact-value intent looks for a page first — the page's facts table already holds
 *       the value, with its confidence and footnote, so this is cheaper and better cited
 *       than a vector search.</li>
 *   <li>A synthesis intent looks for the same page for its narrative and history.</li>
 *   <li>Anything else, or no matching page, escalates to RAG — the behaviour before the
 *       vault existed, which is the safe floor.</li>
 * </ol>
 *
 * <p>The direct fact-table query and templated answers from ADR-023 step 1 are not built
 * yet; until they are, the page is the fact tier's proxy.
 */
@Slf4j
@Service
public class AnswerService {

    private final AnswerRouterService router;
    private final WikiQueryService wikiQueryService;

    /**
     * Constructor injection.
     *
     * @param router classifies the question's intent
     * @param wikiQueryService finds the page that can answer it
     */
    public AnswerService(AnswerRouterService router, WikiQueryService wikiQueryService) {
        this.router = router;
        this.wikiQueryService = wikiQueryService;
    }

    /**
     * Plans how to answer a question.
     *
     * @param question the user's question
     * @return the tier to use and, when applicable, the page to answer from
     */
    public AnswerPlan plan(String question) {
        RoutingDecision decision = router.route(question);
        if (decision.tier() == AnswerTier.RAG) {
            log.debug("Routing: RAG ({})", decision.reason());
            return new AnswerPlan(decision, null);
        }
        return wikiQueryService
                .findPage(question)
                .map(page -> {
                    log.info(
                            "Routing: {} answered from wiki page '{}' ({})",
                            decision.tier(),
                            page.slug(),
                            decision.reason());
                    return new AnswerPlan(decision, page);
                })
                .orElseGet(() -> {
                    RoutingDecision escalated = new RoutingDecision(
                            AnswerTier.RAG, decision.reason() + "; no wiki page matched, escalated to document search");
                    log.debug("Routing: RAG ({})", escalated.reason());
                    return new AnswerPlan(escalated, null);
                });
    }
}
