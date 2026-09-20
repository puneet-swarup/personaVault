/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Configurable alert threshold per policy type.
 *
 * <p>Determines how many days before expiry the renewal checker
 * should raise an alert. Stored in the database so it can be
 * changed at runtime without code changes or restart.
 *
 * <p>Cached in memory by {@link com.personavault.service.AlertThresholdCache}
 * to avoid a DB hit on every scheduled check.
 *
 * @see "ADR-012: Configurable Alert Thresholds"
 */
@Getter
@Setter
@Entity
@Table(name = "alert_thresholds")
public class AlertThreshold {

    /** Policy type this threshold applies to (e.g., "HEALTH", "CAR"). */
    @Id
    @Column(name = "policy_type", length = 50)
    private String policyType;

    /** Number of days before expiry to trigger an alert. */
    @Column(name = "days_before", nullable = false)
    private int daysBefore;

    /** Last time this threshold was modified. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
