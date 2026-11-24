/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AnswerRouterService")
class AnswerRouterServiceTest {

    private final AnswerRouterService router = new AnswerRouterService();

    @ParameterizedTest(name = "exact value: {0}")
    @ValueSource(
            strings = {
                "When does my health policy expire?",
                "What is my policy number?",
                "How much is my premium?",
                "When is the maturity date of my policy?",
                "when does my car insurance renew"
            })
    @DisplayName("should route exact-value questions to the fact tier")
    void shouldRouteExactValueQuestionsToFact(String question) {
        assertEquals(AnswerTier.FACT, router.route(question).tier(), "question: " + question);
    }

    @ParameterizedTest(name = "synthesis: {0}")
    @ValueSource(
            strings = {
                "How has my coverage changed since 2024?",
                "What do I know about HDFC Ergo?",
                "Summarise my insurance situation",
                "Show me the timeline of my premiums",
                "How have my premiums evolved over the years?",
                "When did my coverage change?"
            })
    @DisplayName("should route synthesis questions to the wiki tier")
    void shouldRouteSynthesisQuestionsToWiki(String question) {
        assertEquals(AnswerTier.WIKI, router.route(question).tier(), "question: " + question);
    }

    @ParameterizedTest(name = "search: {0}")
    @ValueSource(
            strings = {
                "What is the capital of France?",
                "What are the exclusions in the maternity clause?",
                "Explain the waiting period wording",
                "who is the nominee"
            })
    @DisplayName("should fall back to document search when no rule matches")
    void shouldFallBackToRag(String question) {
        assertEquals(AnswerTier.RAG, router.route(question).tier(), "question: " + question);
    }

    @ParameterizedTest(name = "blank: [{0}]")
    @ValueSource(strings = {"", "   "})
    @DisplayName("should treat a blank question as a search request")
    void shouldHandleBlankQuestion(String question) {
        RoutingDecision decision = router.route(question);
        assertEquals(AnswerTier.RAG, decision.tier(), "blank questions must not route to the vault");
    }

    @Test
    @DisplayName("should handle a null question without throwing")
    void shouldHandleNullQuestion() {
        assertEquals(AnswerTier.RAG, router.route(null).tier(), "null question must not throw");
    }

    @ParameterizedTest(name = "precedence: {0} -> {1}")
    @CsvSource({
        "'What is my coverage and how has it changed?', FACT",
        "'Summarise how much premium I pay', FACT",
        "'How has my policy number changed?', FACT"
    })
    @DisplayName("should prefer the fact tier when a question matches both rule sets")
    void shouldPreferFactTierOnOverlap(String question, String expectedTier) {
        assertEquals(AnswerTier.valueOf(expectedTier), router.route(question).tier(), "question: " + question);
    }

    @Test
    @DisplayName("should explain its decision")
    void shouldExplainDecision() {
        RoutingDecision decision = router.route("When does my policy expire?");
        assertTrue(decision.reason().contains("exact-value"), decision.reason());
    }

    @Test
    @DisplayName("should ignore punctuation and casing")
    void shouldNormaliseQuestion() {
        assertEquals(
                AnswerTier.FACT,
                router.route("WHEN DOES MY POLICY EXPIRE!!!").tier(),
                "casing and punctuation must not change the tier");
    }
}
