/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Enforces the provenance rule on compiled markdown (ADR-021, ADR-022).
 *
 * <p>The rule: every line that states a value must carry a citation marker, either
 * {@code [^f:<factId>]} for a vault fact or {@code [^d:<docRef>]} for a document.
 * Structural lines (headings, table headers, links, footnote definitions, frontmatter)
 * are exempt.
 *
 * <p>This is the guard that stops the compiler from quietly inventing content: if a
 * future narrative step drops a footnote, the page fails validation instead of
 * reaching the user.
 */
public class WikiPageValidator {

    private static final String FRONTMATTER_FENCE = "---";
    private static final String CODE_FENCE = "```";
    private static final String FACT_MARKER = "[^f:";
    private static final String DOC_MARKER = "[^d:";
    private static final String HISTORY_HEADING = "## History";

    /** Matches the {@code | --- | --- |} separator row of a markdown table. */
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|?[\\s:|-]+\\|?$");

    /**
     * Validates a page, throwing on the first set of violations found.
     *
     * @param markdown the page body to check
     * @throws WikiPageValidationException when the page has no frontmatter or an uncited claim
     */
    public void validate(String markdown) {
        List<String> violations = findViolations(markdown);
        if (!violations.isEmpty()) {
            throw new WikiPageValidationException("Wiki page rejected by provenance validator (ADR-022):"
                    + System.lineSeparator() + String.join(System.lineSeparator(), violations));
        }
    }

    /**
     * Collects every violation in a page without throwing.
     *
     * @param markdown the page body to check
     * @return one human-readable line per violation, empty when the page is valid
     */
    public List<String> findViolations(String markdown) {
        List<String> violations = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            violations.add("page is empty");
            return violations;
        }
        String[] lines = markdown.split("\n", -1);
        if (lines.length == 0 || !FRONTMATTER_FENCE.equals(lines[0].trim())) {
            violations.add("line 1: page must start with YAML frontmatter");
        }

        boolean inFrontmatter = FRONTMATTER_FENCE.equals(lines[0].trim());
        boolean inCodeFence = false;
        boolean inHistory = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (inFrontmatter) {
                if (i > 0 && FRONTMATTER_FENCE.equals(trimmed)) {
                    inFrontmatter = false;
                }
                continue;
            }
            if (trimmed.startsWith(CODE_FENCE)) {
                inCodeFence = !inCodeFence;
                continue;
            }
            if (inCodeFence) {
                continue;
            }
            if (trimmed.startsWith("## ")) {
                inHistory = HISTORY_HEADING.equalsIgnoreCase(trimmed);
                continue;
            }
            if (isClaimLine(trimmed, inHistory) && !hasCitation(trimmed)) {
                violations.add("line " + (i + 1) + ": claim without a [^f:...] citation: " + trimmed);
            }
        }
        return violations;
    }

    private static boolean isClaimLine(String trimmed, boolean inHistory) {
        if (trimmed.isEmpty() || trimmed.startsWith("[^")) {
            return false;
        }
        if (trimmed.startsWith("|")) {
            return !TABLE_SEPARATOR.matcher(trimmed).matches() && !isTableHeader(trimmed);
        }
        return inHistory && trimmed.startsWith("- ");
    }

    // The header row names the columns; it carries no values, so it needs no citation.
    private static boolean isTableHeader(String trimmed) {
        return trimmed.toLowerCase(Locale.ROOT).contains("field")
                && trimmed.toLowerCase(Locale.ROOT).contains("value");
    }

    private static boolean hasCitation(String line) {
        return line.contains(FACT_MARKER) || line.contains(DOC_MARKER);
    }
}
