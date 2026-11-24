/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personavault.entity.Fact;
import com.personavault.entity.VaultEntity;
import com.personavault.repository.FactRepository;
import com.personavault.repository.VaultEntityRepository;

// The single write path for vault facts and entities (ADR-017).
// Enforces the provenance rule from ADR-021: a fact that cannot cite a source
// document is rejected. This is the guardrail that keeps the vault trustworthy;
// every higher-level feature (extraction, corrections, wiki) goes through here.
@Service
public class FactService {

    // Default confidence for an LLM-extracted fact before adjustments (ADR-021).
    static final BigDecimal DEFAULT_LLM_CONFIDENCE = new BigDecimal("0.75");

    private final FactRepository factRepository;
    private final VaultEntityRepository entityRepository;
    private final Clock clock;

    public FactService(FactRepository factRepository, VaultEntityRepository entityRepository, Clock clock) {
        this.factRepository = factRepository;
        this.entityRepository = entityRepository;
        this.clock = clock;
    }

    // Finds an existing live entity or creates one, then returns it.
    // entityType    - domain type, e.g. POLICY
    // canonicalName - normalised display name
    // schemaId      - extraction schema that produced it (may be null)
    @Transactional
    public VaultEntity findOrCreateEntity(String entityType, String canonicalName, String schemaId) {
        requireText(entityType, "entityType");
        requireText(canonicalName, "canonicalName");
        return entityRepository
                .findByEntityTypeAndCanonicalNameAndDeletedAtIsNull(entityType, canonicalName)
                .orElseGet(() -> {
                    Instant now = Instant.now(clock);
                    VaultEntity entity = new VaultEntity();
                    entity.setEntityType(entityType);
                    entity.setCanonicalName(canonicalName);
                    entity.setSchemaId(schemaId);
                    entity.setCreatedAt(now);
                    entity.setUpdatedAt(now);
                    return entityRepository.save(entity);
                });
    }

    // Records a fact, enforcing provenance (ADR-021).
    // Throws ProvenanceRequiredException if the source document is missing.
    @Transactional
    public Fact recordFact(Fact fact) {
        if (fact.getSourceDocId() == null) {
            throw new ProvenanceRequiredException(
                    "Fact '" + fact.getFactKey() + "' has no source document; provenance is mandatory (ADR-021)");
        }
        requireText(fact.getFactKey(), "factKey");
        requireText(fact.getValueType(), "valueType");
        requireText(fact.getExtractedBy(), "extractedBy");
        if (fact.getEntityId() == null) {
            throw new IllegalArgumentException("Fact must reference an entity");
        }
        if (fact.getConfidence() == null) {
            fact.setConfidence(DEFAULT_LLM_CONFIDENCE);
        }
        if (fact.getVerificationStatus() == null) {
            fact.setVerificationStatus("EXTRACTED");
        }
        if (fact.getFirstSeenAt() == null) {
            fact.setFirstSeenAt(Instant.now(clock));
        }
        return factRepository.save(fact);
    }

    // Returns the current (open-ended) facts for an entity and key.
    @Transactional(readOnly = true)
    public List<Fact> currentFacts(Long entityId, String factKey) {
        return factRepository.findByEntityIdAndFactKeyAndValidToIsNull(entityId, factKey);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Field '" + field + "' must not be blank");
        }
    }

    // Thrown when a fact is missing mandatory provenance (ADR-021).
    public static class ProvenanceRequiredException extends RuntimeException {
        public ProvenanceRequiredException(String message) {
            super(message);
        }
    }
}
