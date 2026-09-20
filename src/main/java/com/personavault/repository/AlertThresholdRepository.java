/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.personavault.entity.AlertThreshold;

/**
 * Spring Data JPA repository for {@link AlertThreshold} entities.
 */
@Repository
public interface AlertThresholdRepository extends JpaRepository<AlertThreshold, String> {

    /**
     * Updates the days-before value for a policy type.
     * Called when the user changes a threshold (Phase 3 UI).
     */
    @Modifying
    @Query("UPDATE AlertThreshold a SET a.daysBefore = :days, "
            + "a.updatedAt = CURRENT_TIMESTAMP WHERE a.policyType = :type")
    void updateDaysBefore(@Param("type") String policyType, @Param("days") int days);
}
