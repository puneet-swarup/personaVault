/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.personavault.entity.WikiPageType;

/**
 * Builds {@code index.md}, the table of contents at the wiki root (ADR-022).
 *
 * <p>The index is generated from the mirror table rather than from the filesystem so
 * that it can never drift from what the database believes exists. Entries are grouped
 * by page type and sorted by title, which keeps the file diff-stable between runs.
 */
public class WikiIndexBuilder {

    /**
     * Renders the index page.
     *
     * @param entries every page in the wiki
     * @param compiledAt when this index was generated
     * @return markdown with frontmatter, grouped by page type
     */
    public String render(List<WikiIndexEntry> entries, Instant compiledAt) {
        Map<WikiPageType, List<WikiIndexEntry>> byType = new EnumMap<>(WikiPageType.class);
        entries.stream()
                .filter(entry -> entry.pageType() != WikiPageType.INDEX)
                .sorted((a, b) -> a.title().compareToIgnoreCase(b.title()))
                .forEach(entry -> byType.computeIfAbsent(entry.pageType(), k -> new ArrayList<>())
                        .add(entry));

        StringBuilder out = new StringBuilder();
        out.append("---\n");
        out.append("slug: index\n");
        out.append("type: index\n");
        out.append("lastCompiledAt: ")
                .append(compiledAt.truncatedTo(ChronoUnit.SECONDS))
                .append('\n');
        out.append("needsReview: false\n");
        out.append("version: 1\n");
        out.append("---\n\n");
        out.append("# Wiki Index\n\n");
        int total = byType.values().stream().mapToInt(List::size).sum();
        out.append("_").append(total).append(total == 1 ? " page" : " pages").append("._\n\n");

        if (total == 0) {
            out.append("_Nothing compiled yet. Upload a document to populate the vault._\n");
            return out.toString();
        }
        for (Map.Entry<WikiPageType, List<WikiIndexEntry>> group : byType.entrySet()) {
            out.append("## ")
                    .append(group.getKey().name().charAt(0))
                    .append(group.getKey().name().substring(1).toLowerCase(Locale.ROOT))
                    .append("\n\n");
            for (WikiIndexEntry entry : group.getValue()) {
                out.append("- [[")
                        .append(entry.slug())
                        .append('|')
                        .append(entry.title())
                        .append("]]\n");
            }
            out.append('\n');
        }
        return out.toString();
    }
}
