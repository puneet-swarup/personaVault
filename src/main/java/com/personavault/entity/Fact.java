/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// A single typed, provenance-tracked value about a VaultEntity.
// Per ADR-017 this is the unit of truth. Per ADR-019 every fact has an optional
// validity interval (validFrom/validTo) so a change over time (premium 5L then
// 10L) is modelled as two facts rather than a contradiction. Per ADR-021 every
// fact carries confidence and provenance; a fact that cannot cite a source is
// rejected at write time. Exactly one value column is populated, selected by
// valueType: STRING/MONEY use text/num, DATE uses date, ENTITY_REF uses entityId.
@Getter
@Setter
@Entity
@Table(name = "fact")
public class Fact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    // The entity this fact describes.
    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    // Attribute name: expiryDate, annualPremium, dosage, ...
    @Column(name = "fact_key", nullable = false, length = 80)
    private String factKey;

    // STRING, NUMBER, MONEY, DATE, BOOLEAN, ENTITY_REF.
    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_num", precision = 18, scale = 4)
    private BigDecimal valueNum;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "value_entity_id")
    private Long valueEntityId;

    // ISO currency code when valueType is MONEY.
    @Column(name = "currency", length = 3)
    private String currency;

    // Start of the interval during which this value is true (ADR-019).
    @Column(name = "valid_from")
    private LocalDate validFrom;

    // End of validity; null means still current (ADR-019).
    @Column(name = "valid_to")
    private LocalDate validTo;

    // 0.00-1.00 reliability score (ADR-021).
    @Column(name = "confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    // EXTRACTED, USER_CORRECTED, CONFIRMED, STALE.
    @Column(name = "verification_status", nullable = false, length = 20)
    private String verificationStatus;

    // REGEX, LLM, USER (ADR-021).
    @Column(name = "extracted_by", nullable = false, length = 20)
    private String extractedBy;

    // Required provenance: the document this value came from (ADR-021).
    @Column(name = "source_doc_id", nullable = false)
    private Long sourceDocId;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "source_snippet")
    private String sourceSnippet;

    // When a newer fact replaces this one (ADR-019).
    @Column(name = "superseded_by")
    private Long supersededBy;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_confirmed_at")
    private Instant lastConfirmedAt;
}
