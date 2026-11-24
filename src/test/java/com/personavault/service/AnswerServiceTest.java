/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnswerService")
class AnswerServiceTest {

    private static final WikiPageContext PAGE =
            new WikiPageContext("policy-hp-1", "HP-1", "---\nslug: policy-hp-1\n---\n# HP-1\n");

    @Mock
    private AnswerRouterService router;

    @Mock
    private WikiQueryService wikiQueryService;

    private AnswerService service() {
        return new AnswerService(router, wikiQueryService);
    }

    @Test
    @DisplayName("should answer an exact-value question from the matching page")
    void shouldAnswerFactIntentFromWikiPage() {
        when(router.route(anyString())).thenReturn(new RoutingDecision(AnswerTier.FACT, "exact-value phrasing"));
        when(wikiQueryService.findPage(anyString())).thenReturn(Optional.of(PAGE));

        AnswerPlan plan = service().plan("When does my policy expire?");

        assertTrue(plan.answersFromWiki(), "a matching page should be used");
        assertEquals(AnswerTier.FACT, plan.decision().tier(), "tier should stay FACT");
        assertEquals("policy-hp-1", plan.wikiPage().orElseThrow().slug(), "wrong page attached");
    }

    @Test
    @DisplayName("should answer a synthesis question from the matching page")
    void shouldAnswerWikiIntentFromWikiPage() {
        when(router.route(anyString())).thenReturn(new RoutingDecision(AnswerTier.WIKI, "synthesis phrasing"));
        when(wikiQueryService.findPage(anyString())).thenReturn(Optional.of(PAGE));

        AnswerPlan plan = service().plan("How has my coverage changed?");

        assertTrue(plan.answersFromWiki(), "a matching page should be used");
        assertEquals(AnswerTier.WIKI, plan.decision().tier(), "tier should stay WIKI");
    }

    @Test
    @DisplayName("should escalate to document search when no page matches")
    void shouldEscalateWhenNoPageMatches() {
        when(router.route(anyString())).thenReturn(new RoutingDecision(AnswerTier.WIKI, "synthesis phrasing"));
        when(wikiQueryService.findPage(anyString())).thenReturn(Optional.empty());

        AnswerPlan plan = service().plan("How has my coverage changed?");

        assertFalse(plan.answersFromWiki(), "no page means no wiki answer");
        assertEquals(AnswerTier.RAG, plan.decision().tier(), "should fall back to RAG");
        assertTrue(
                plan.decision().reason().contains("escalated"), plan.decision().reason());
    }

    @Test
    @DisplayName("should not touch the vault at all when the router says RAG")
    void shouldSkipWikiLookupForRagIntent() {
        when(router.route(anyString())).thenReturn(new RoutingDecision(AnswerTier.RAG, "no rule matched"));

        AnswerPlan plan = service().plan("What are the exclusions in the maternity clause?");

        assertFalse(plan.answersFromWiki(), "RAG intent must not use a page");
        verify(wikiQueryService, never()).findPage(anyString());
    }
}
