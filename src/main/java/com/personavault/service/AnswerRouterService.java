/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * Decides which tier answers a question, before any LLM is involved (ADR-023).
 *
 * <p>Step 1 of the ADR's classification strategy: ordered, rule-based matching.
 * Exact-value phrasings ("when does my policy expire", "what is my premium") route to
 * the fact tier; synthesis phrasings ("how has my coverage changed", "timeline",
 * "what do I know about X") route to the wiki; everything else falls back to RAG,
 * which degrades to today's behaviour.
 *
 * <p>Rules are evaluated in the ADR's order — FACT, then WIKI — so a question that
 * matches both ("what is my premium history") takes the more authoritative tier. The
 * small-LLM classifier from step 2 is deliberately not implemented yet: these rules are
 * deterministic and testable, and RAG is a safe default while the vault is small.
 */
@Service
public class AnswerRouterService {

    /** Exact-value phrasings: these belong to stored facts, not prose. */
    private static final List<Pattern> FACT_PATTERNS = List.of(
            Pattern.compile("\\b(when|what date)\\b.*\\b(expir\\w*|renew\\w*|due|matu\\w*)\\b"),
            Pattern.compile("\\bwhat('s| is| was)? my\\b.*\\b(premium|policy number|sum insured|"
                    + "coverage|insured amount|deductible)\\b"),
            Pattern.compile("\\bhow much\\b.*\\b(premium|pay|paid|coverage|insured|deductible)\\b"),
            Pattern.compile("\\bpolicy number\\b"));

    /** Synthesis phrasings: these need a page that narrates across documents. */
    private static final List<Pattern> WIKI_PATTERNS = List.of(
            Pattern.compile("\\bhow (has|have|did)\\b.*\\b(chang\\w*|evolv\\w*|grown|increased|decreased)\\b"),
            Pattern.compile("\\bwhen did\\b.*\\b(chang\\w*|increas\\w*|decreas\\w*|start\\w*|begin)\\b"),
            Pattern.compile("\\bhistory of\\b"),
            Pattern.compile("\\btimeline\\b"),
            Pattern.compile("\\bwhat do (i|you) know about\\b"),
            Pattern.compile("\\b(summar\\w*|overview)\\b"),
            Pattern.compile("\\b(over|across) (the )?(years|time)\\b"));

    /**
     * Classifies a question.
     *
     * @param question the user's question
     * @return the tier to answer from, with a reason for the log
     */
    public RoutingDecision route(String question) {
        if (question == null || question.isBlank()) {
            return new RoutingDecision(AnswerTier.RAG, "empty question; defaulting to document search");
        }
        String normalised = normalise(question);
        for (Pattern pattern : FACT_PATTERNS) {
            if (pattern.matcher(normalised).find()) {
                return new RoutingDecision(AnswerTier.FACT, "matched exact-value phrasing: " + pattern.pattern());
            }
        }
        for (Pattern pattern : WIKI_PATTERNS) {
            if (pattern.matcher(normalised).find()) {
                return new RoutingDecision(AnswerTier.WIKI, "matched synthesis phrasing: " + pattern.pattern());
            }
        }
        return new RoutingDecision(AnswerTier.RAG, "no rule matched; falling back to document search");
    }

    // Lowercase and collapse punctuation so the patterns only deal with words.
    private static String normalise(String question) {
        return question.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s']", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
