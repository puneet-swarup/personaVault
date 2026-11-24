/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.util.Objects;

/**
 * The router's verdict for one question (ADR-023).
 *
 * @param tier the tier that should answer
 * @param reason short, loggable explanation of why that tier was chosen
 */
public record RoutingDecision(AnswerTier tier, String reason) {

    /**
     * Rejects nulls so a decision is always explainable in logs and telemetry.
     */
    public RoutingDecision {
        Objects.requireNonNull(tier, "tier is required");
        Objects.requireNonNull(reason, "reason is required");
    }
}
