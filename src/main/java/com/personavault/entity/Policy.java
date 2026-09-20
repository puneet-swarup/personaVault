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

/**
 * A structured policy record extracted from an ingested document.
 *
 * <p>Per ADR-007, this table is the <b>source of truth</b> for exact facts
 * (dates, amounts, policy numbers). The LLM never answers these from memory —
 * the renewal checker and dashboard query this table directly.
 *
 * <p>The {@link #verified} flag indicates whether the user has confirmed
 * the extracted values. Unverified policies show a ⚠️ on the dashboard
 * and include a caveat in renewal alerts.
 *
 * @see "ADR-007: Structured Extraction Over Pure RAG"
 */
@Getter
@Setter
@Entity
@Table(name = "policies")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    /** Unique policy identifier as printed on the document. */
    @Column(name = "policy_number", nullable = false, length = 100)
    private String policyNumber;

    /** Insurance company or financial institution name. */
    @Column(name = "insurer_name", length = 256)
    private String insurerName;

    /** Policy type: HEALTH, CAR, LIFE, HOME, TRAVEL, OTHER. */
    @Column(name = "policy_type", nullable = false, length = 50)
    private String policyType;

    /** Policy start date. */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    /** Policy end date. The renewal checker alerts based on this. */
    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    /** Annualized premium amount in INR. */
    @Column(name = "annual_premium", precision = 12, scale = 2)
    private BigDecimal annualPremium;

    /** Payment frequency: ANNUAL, SEMI_ANNUAL, QUARTERLY, MONTHLY. */
    @Column(name = "premium_frequency", length = 20)
    private String premiumFrequency;

    /** The document this policy was extracted from. */
    @Column(name = "document_id", nullable = false)
    private Long documentId;

    /** When the LLM extracted this record. */
    @Column(name = "extracted_at", nullable = false)
    private Instant extractedAt;

    /** True when the user has confirmed the extracted values are correct. */
    @Column(name = "verified", nullable = false)
    private boolean verified;

    /**
     * Returns true if this policy's values have been user-confirmed.
     * Unverified policies get a ⚠️ on the dashboard and a caveat in alerts.
     */
    public boolean isVerified() {
        return verified;
    }
}
