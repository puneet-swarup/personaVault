/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personavault.entity.Fact;
import com.personavault.entity.Policy;
import com.personavault.entity.VaultEntity;
import com.personavault.repository.FactRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Turns an extracted policy into Tier 2 truth: one entity plus typed facts (ADR-017).
 *
 * <p>Until now the extraction step wrote only to the legacy {@code policies} table, so
 * the generic {@code entity} + {@code fact} tables were populated once by the V6
 * backfill and then went stale. This service closes that gap: every extraction now also
 * lands in the vault, which is what the wiki compiles from.
 *
 * <p>Re-extracting the same document is a no-op: a fact that already exists for this
 * document, entity and key with the same value is not written twice. A genuinely
 * different value supersedes the older fact instead of overwriting it (ADR-019), so the
 * history stays intact.
 */
@Slf4j
@Service
public class FactIngestionService {

    /** Entity type used for policies in the vault (ADR-017). */
    static final String POLICY_ENTITY_TYPE = "POLICY";

    /** Extraction schema id recorded on the entity (ADR-018). */
    static final String POLICY_SCHEMA_ID = "insurance-policy";

    /** Base confidence for a schema-guided LLM extraction (ADR-021). */
    static final BigDecimal LLM_CONFIDENCE = new BigDecimal("0.75");

    /** Fact value type for monetary amounts (ADR-017). */
    static final String MONEY_TYPE = "MONEY";

    /** ISO currency assumed for policy amounts until the schema carries currency. */
    static final String DEFAULT_CURRENCY = "INR";

    private final FactService factService;
    private final FactRepository factRepository;
    private final Clock clock;

    /**
     * Constructor injection.
     *
     * @param factService the single write path for facts
     * @param factRepository reads used to detect duplicates and supersession
     * @param clock clock for first-seen timestamps
     */
    public FactIngestionService(FactService factService, FactRepository factRepository, Clock clock) {
        this.factService = factService;
        this.factRepository = factRepository;
        this.clock = clock;
    }

    /**
     * Records an extracted policy as an entity with one fact per populated field.
     *
     * @param documentId the document the values were extracted from, used as provenance
     * @param policy the extracted policy
     * @return the vault entity the facts hang off
     */
    @Transactional
    public VaultEntity recordPolicyFacts(Long documentId, Policy policy) {
        Objects.requireNonNull(documentId, "documentId is required as fact provenance");
        Objects.requireNonNull(policy, "policy is required");

        String canonicalName = canonicalName(policy, documentId);
        VaultEntity entity = factService.findOrCreateEntity(POLICY_ENTITY_TYPE, canonicalName, POLICY_SCHEMA_ID);

        recordText(entity, documentId, "policyNumber", canonicalName);
        recordText(entity, documentId, "insurerName", trimToNull(policy.getInsurerName()));
        recordText(entity, documentId, "policyType", trimToNull(policy.getPolicyType()));
        recordText(entity, documentId, "premiumFrequency", trimToNull(policy.getPremiumFrequency()));
        recordDate(entity, documentId, "effectiveDate", policy.getEffectiveDate());
        recordDate(entity, documentId, "expiryDate", policy.getExpiryDate());
        recordMoney(entity, documentId, "annualPremium", policy.getAnnualPremium());

        log.debug("Recorded vault facts for {} from document {}", canonicalName, documentId);
        return entity;
    }

    private static String canonicalName(Policy policy, Long documentId) {
        String number = trimToNull(policy.getPolicyNumber());
        return number != null ? number : "POLICY-" + documentId;
    }

    private void recordText(VaultEntity entity, Long documentId, String key, String value) {
        if (value == null) {
            return;
        }
        recordIfChanged(entity, documentId, key, "STRING", value, null, null);
    }

    private void recordDate(VaultEntity entity, Long documentId, String key, LocalDate value) {
        if (value == null) {
            return;
        }
        recordIfChanged(entity, documentId, key, "DATE", null, null, value);
    }

    private void recordMoney(VaultEntity entity, Long documentId, String key, BigDecimal value) {
        if (value == null) {
            return;
        }
        recordIfChanged(entity, documentId, key, "MONEY", null, value, null);
    }

    // Writes a fact unless this document already contributed the same value for this key.
    private void recordIfChanged(
            VaultEntity entity,
            Long documentId,
            String key,
            String valueType,
            String text,
            BigDecimal num,
            LocalDate date) {
        String candidate = candidateValue(valueType, text, num, date);
        List<Fact> current = factRepository.findByEntityIdAndFactKeyAndValidToIsNull(entity.getId(), key);
        for (Fact existing : current) {
            if (documentId.equals(existing.getSourceDocId()) && candidate.equals(canonicalValue(existing))) {
                log.debug("Fact {}.{} unchanged for document {} — not rewritten", entity.getId(), key, documentId);
                return;
            }
        }

        Fact fact = new Fact();
        fact.setEntityId(entity.getId());
        fact.setFactKey(key);
        fact.setValueType(valueType);
        fact.setValueText(text);
        fact.setValueNum(num);
        fact.setValueDate(date);
        if (MONEY_TYPE.equals(valueType)) {
            fact.setCurrency(DEFAULT_CURRENCY);
        }
        fact.setConfidence(LLM_CONFIDENCE);
        fact.setExtractedBy("LLM");
        fact.setSourceDocId(documentId);
        fact.setFirstSeenAt(Instant.now(clock));
        Fact saved = factService.recordFact(fact);

        closeSupersededFacts(current, documentId, saved);
    }

    // An older value from a different document stops being current (ADR-019 rule 2).
    private void closeSupersededFacts(List<Fact> current, Long documentId, Fact replacement) {
        LocalDate closedOn = LocalDate.now(clock).minusDays(1);
        for (Fact existing : current) {
            if (documentId.equals(existing.getSourceDocId())) {
                continue;
            }
            existing.setValidTo(closedOn);
            existing.setSupersededBy(replacement.getId());
            factRepository.save(existing);
            log.info(
                    "Fact {}.{} superseded by fact {} (was: {})",
                    existing.getEntityId(),
                    existing.getFactKey(),
                    replacement.getId(),
                    canonicalValue(existing));
        }
    }

    private static String candidateValue(String valueType, String text, BigDecimal num, LocalDate date) {
        return switch (valueType) {
            case MONEY_TYPE, "NUMBER" ->
                num == null ? "" : num.stripTrailingZeros().toPlainString();
            case "DATE" -> date == null ? "" : date.toString();
            default -> text == null ? "" : text;
        };
    }

    private static String canonicalValue(Fact fact) {
        String type = fact.getValueType() == null ? "" : fact.getValueType();
        return switch (type) {
            case MONEY_TYPE, "NUMBER" ->
                fact.getValueNum() == null
                        ? ""
                        : fact.getValueNum().stripTrailingZeros().toPlainString();
            case "DATE" ->
                fact.getValueDate() == null ? "" : fact.getValueDate().toString();
            default -> fact.getValueText() == null ? "" : fact.getValueText();
        };
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
