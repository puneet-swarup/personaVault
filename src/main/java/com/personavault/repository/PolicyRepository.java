/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.personavault.entity.Policy;

/**
 * Spring Data JPA repository for {@link Policy} entities.
 *
 * <p>Provides the queries used by the renewal checker, dashboard,
 * and extraction service.
 */
@Repository
public interface PolicyRepository extends JpaRepository<Policy, Long> {

    /**
     * Finds all policies expiring between the given dates (inclusive).
     * Used by the daily renewal checker.
     *
     * @param from earliest expiry date to include
     * @param to   latest expiry date to include
     * @return policies expiring in the window
     */
    List<Policy> findByExpiryDateBetween(LocalDate from, LocalDate to);

    /**
     * Finds all policies for a specific document.
     * Used during cascade deletion.
     */
    List<Policy> findByDocumentId(Long documentId);

    /**
     * Deletes all policies associated with a document.
     * Called during document cascade deletion.
     */
    void deleteByDocumentId(Long documentId);

    /**
     * Dashboard: total annual premium across all policies.
     *
     * @return sum of all annual premiums
     */
    @Query("SELECT COALESCE(SUM(p.annualPremium), 0) FROM Policy p")
    BigDecimal sumAnnualPremium();

    /**
     * Dashboard: the policy with the nearest expiry date.
     *
     * @return the soonest-expiring policy, or null if none exist
     */
    @Query("SELECT p FROM Policy p WHERE p.expiryDate >= CURRENT_DATE ORDER BY p.expiryDate ASC LIMIT 1")
    Policy findNextRenewal();

    /**
     * Dashboard: all active policies, sorted by expiry date.
     */
    List<Policy> findByExpiryDateAfterOrderByExpiryDateAsc(LocalDate today);
}
