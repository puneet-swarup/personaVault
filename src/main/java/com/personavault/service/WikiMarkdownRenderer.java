/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.personavault.entity.Fact;

/**
 * Renders one wiki page as markdown with YAML frontmatter (ADR-022).
 *
 * <p>The renderer is deliberately deterministic and free of LLM calls: given the same
 * facts it emits the same bytes. That is what makes the provenance rule checkable —
 * every claim line carries a {@code [^f:<factId>]} marker, and
 * {@link WikiPageValidator} rejects a page where one is missing.
 *
 * <p>Narrative prose is a later step. This class produces the skeleton the narrative
 * will hang off: current values, the history of values that changed over time
 * (ADR-019), links to related entities, and footnotes back to source documents
 * (ADR-021).
 */
public class WikiMarkdownRenderer {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Marks a fact that has been superseded or invalidated; excluded from current values. */
    static final String STALE = "STALE";

    /** Below this confidence a page is flagged for review (ADR-021). */
    static final BigDecimal REVIEW_THRESHOLD = new BigDecimal("0.60");

    private static final Comparator<Fact> FACT_ORDER = Comparator.comparing(
                    Fact::getFactKey, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Fact::getValidFrom, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Fact::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Renders a page.
     *
     * @param model the facts, links and metadata to draw
     * @return markdown ending in a newline, with LF line endings
     */
    public String render(WikiRenderModel model) {
        List<Fact> sorted = new ArrayList<>(model.facts());
        sorted.sort(FACT_ORDER);
        List<Fact> current =
                sorted.stream().filter(WikiMarkdownRenderer::isCurrent).toList();
        List<Fact> historical = sorted.stream().filter(f -> !isCurrent(f)).toList();

        StringBuilder out = new StringBuilder();
        appendFrontmatter(out, model, sorted);
        out.append("# ").append(model.title()).append("\n\n");
        appendFacts(out, current, model.entityLinks());
        if (!historical.isEmpty()) {
            appendHistory(out, historical, model.entityLinks());
        }
        appendLinks(out, current, model.entityLinks());
        appendFootnotes(out, sorted, model.sourceNames());
        return out.toString();
    }

    /**
     * True when a fact is the value in force today.
     *
     * @param fact the fact to test
     * @return true when the fact is open-ended and not stale
     */
    static boolean isCurrent(Fact fact) {
        return fact.getValidTo() == null && !STALE.equals(fact.getVerificationStatus());
    }

    /**
     * True when any fact on the page is too weak to present without a warning.
     *
     * @param facts facts to inspect
     * @return true when a current fact is below the review threshold
     */
    public static boolean needsReview(List<Fact> facts) {
        return facts.stream()
                .filter(WikiMarkdownRenderer::isCurrent)
                .anyMatch(f -> f.getConfidence() == null || f.getConfidence().compareTo(REVIEW_THRESHOLD) < 0);
    }

    private void appendFrontmatter(StringBuilder out, WikiRenderModel model, List<Fact> facts) {
        out.append("---\n");
        out.append("slug: ").append(model.slug()).append('\n');
        out.append("type: ").append(model.pageType().folder()).append('\n');
        if (model.entityRef() != null) {
            out.append("entity: \"").append(model.entityRef()).append("\"\n");
        }
        Instant compiledAt = model.compiledAt().truncatedTo(ChronoUnit.SECONDS);
        out.append("lastCompiledAt: ").append(compiledAt).append('\n');
        out.append("needsReview: ").append(model.needsReview()).append('\n');
        out.append("version: ").append(model.version()).append('\n');
        out.append("sourceRefs: ").append(sourceRefs(facts)).append('\n');
        out.append("factRefs: ").append(factRefs(facts)).append('\n');
        out.append("---\n\n");
    }

    private void appendFacts(StringBuilder out, List<Fact> current, Map<Long, WikiRenderModel.WikiLink> links) {
        out.append("## Facts\n\n");
        if (current.isEmpty()) {
            out.append("_No facts recorded yet._\n\n");
            return;
        }
        out.append("| Field | Value | Confidence |\n");
        out.append("| --- | --- | --- |\n");
        for (Fact fact : current) {
            out.append("| ")
                    .append(humanize(fact.getFactKey()))
                    .append(" | ")
                    .append(valueOf(fact, links))
                    .append(" [^f:")
                    .append(fact.getId())
                    .append("] | ")
                    .append(confidenceOf(fact))
                    .append(" |\n");
        }
        out.append('\n');
    }

    private void appendHistory(StringBuilder out, List<Fact> historical, Map<Long, WikiRenderModel.WikiLink> links) {
        out.append("## History\n\n");
        for (Fact fact : historical) {
            out.append("- ")
                    .append(humanize(fact.getFactKey()))
                    .append(": ")
                    .append(valueOf(fact, links))
                    .append(" (")
                    .append(periodOf(fact))
                    .append(") [^f:")
                    .append(fact.getId())
                    .append("]\n");
        }
        out.append('\n');
    }

    private void appendLinks(StringBuilder out, List<Fact> current, Map<Long, WikiRenderModel.WikiLink> links) {
        List<Fact> references =
                current.stream().filter(f -> f.getValueEntityId() != null).toList();
        if (references.isEmpty()) {
            return;
        }
        out.append("## Links\n\n");
        for (Fact fact : references) {
            WikiRenderModel.WikiLink link = links.get(fact.getValueEntityId());
            String target = link != null ? link.slug() : "entity-" + fact.getValueEntityId();
            String label = link != null ? link.label() : "entity #" + fact.getValueEntityId();
            out.append("- ").append(humanize(fact.getFactKey())).append(": [[");
            out.append(target).append("|").append(label).append("]]\n");
        }
        out.append('\n');
    }

    private void appendFootnotes(StringBuilder out, List<Fact> facts, Map<Long, String> sourceNames) {
        if (facts.isEmpty()) {
            return;
        }
        out.append("## Sources\n\n");
        List<Fact> ordered = new ArrayList<>(facts);
        ordered.sort(Comparator.comparing(Fact::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        for (Fact fact : ordered) {
            out.append("[^f:").append(fact.getId()).append("]: from ");
            String name = sourceNames.get(fact.getSourceDocId());
            out.append(name != null ? name : "document #" + fact.getSourceDocId());
            if (fact.getSourcePage() != null) {
                out.append(", page ").append(fact.getSourcePage());
            }
            out.append('\n');
        }
    }

    /**
     * Formats a fact value for display, by value type.
     *
     * @param fact the fact whose value is being drawn
     * @param links resolved entity links, used for reference values
     * @return display text, never null
     */
    String valueOf(Fact fact, Map<Long, WikiRenderModel.WikiLink> links) {
        String type = fact.getValueType() == null ? "" : fact.getValueType();
        String raw =
                switch (type) {
                    case "MONEY" -> money(fact);
                    case "DATE" -> date(fact);
                    case "NUMBER" -> number(fact);
                    case "ENTITY_REF" -> reference(fact, links);
                    default -> Objects.requireNonNullElse(fact.getValueText(), "");
                };
        return raw.replace("|", "\\|");
    }

    private static String money(Fact fact) {
        BigDecimal amount = fact.getValueNum();
        if (amount == null) {
            return Objects.requireNonNullElse(fact.getValueText(), "");
        }
        String formatted = indianGrouping(amount);
        return fact.getCurrency() == null ? formatted : fact.getCurrency() + " " + formatted;
    }

    /**
     * Groups a number the Indian way: the last three digits, then pairs.
     *
     * <p>Computed here rather than taken from a {@link NumberFormat} because the JDK's
     * locale data renders {@code en-IN} with Western grouping, which would print
     * {@code 120,000} where an Indian reader expects {@code 1,20,000}.
     *
     * @param amount the value to format
     * @return grouped digits such as {@code 12,00,000} or {@code 1,20,000.50}
     */
    static String indianGrouping(BigDecimal amount) {
        BigDecimal trimmed = amount.stripTrailingZeros();
        if (trimmed.scale() < 0) {
            trimmed = trimmed.setScale(0);
        }
        String plain = trimmed.toPlainString();
        int dot = plain.indexOf('.');
        String integerPart = dot < 0 ? plain : plain.substring(0, dot);
        String fraction = dot < 0 ? "" : plain.substring(dot);
        boolean negative = integerPart.startsWith("-");
        if (negative) {
            integerPart = integerPart.substring(1);
        }
        String grouped = integerPart.length() <= 3 ? integerPart : groupIndian(integerPart);
        return (negative ? "-" : "") + grouped + fraction;
    }

    private static String groupIndian(String digits) {
        String tail = digits.substring(digits.length() - 3);
        String head = digits.substring(0, digits.length() - 3);
        List<String> groups = new ArrayList<>();
        for (int end = head.length(); end > 0; end -= 2) {
            groups.add(0, head.substring(Math.max(0, end - 2), end));
        }
        return String.join(",", groups) + "," + tail;
    }

    private static String date(Fact fact) {
        LocalDate value = fact.getValueDate();
        return value == null ? Objects.requireNonNullElse(fact.getValueText(), "") : DATE_FORMAT.format(value);
    }

    private static String number(Fact fact) {
        BigDecimal value = fact.getValueNum();
        if (value == null) {
            return Objects.requireNonNullElse(fact.getValueText(), "");
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String reference(Fact fact, Map<Long, WikiRenderModel.WikiLink> links) {
        WikiRenderModel.WikiLink link = links.get(fact.getValueEntityId());
        return link != null ? "[[" + link.slug() + "|" + link.label() + "]]" : "entity #" + fact.getValueEntityId();
    }

    /**
     * Renders a confidence score as a whole percentage.
     *
     * @param fact the fact whose confidence is shown
     * @return text such as {@code 95%}
     */
    String confidenceOf(Fact fact) {
        if (fact.getConfidence() == null) {
            return "unknown";
        }
        return Math.round(fact.getConfidence().doubleValue() * 100) + "%";
    }

    /**
     * Turns a camelCase fact key into a human label.
     *
     * @param key fact key such as {@code expiryDate}
     * @return label such as {@code Expiry date}
     */
    static String humanize(String key) {
        if (key == null || key.isBlank()) {
            return "Value";
        }
        String spaced =
                key.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').trim();
        String lower = spaced.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String periodOf(Fact fact) {
        LocalDate from = fact.getValidFrom();
        LocalDate to = fact.getValidTo();
        if (from != null && to != null) {
            return DATE_FORMAT.format(from) + " to " + DATE_FORMAT.format(to);
        }
        if (from != null) {
            return "from " + DATE_FORMAT.format(from);
        }
        if (to != null) {
            return "until " + DATE_FORMAT.format(to);
        }
        Instant firstSeen = fact.getFirstSeenAt();
        return firstSeen == null ? "validity unknown" : "recorded " + firstSeen.truncatedTo(ChronoUnit.DAYS);
    }

    private static String sourceRefs(List<Fact> facts) {
        List<String> refs = facts.stream()
                .map(Fact::getSourceDocId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(id -> "doc:" + id)
                .toList();
        return "[" + String.join(", ", refs) + "]";
    }

    private static String factRefs(List<Fact> facts) {
        List<String> refs = facts.stream()
                .map(Fact::getId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(id -> "fact:" + id)
                .toList();
        return "[" + String.join(", ", refs) + "]";
    }
}
