/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.personavault.entity.Fact;

// Data access for Fact (ADR-017, 019).
// Point-in-time and current-value queries are expressed here so callers do not
// repeat the validity-interval logic (ADR-019).
public interface FactRepository extends JpaRepository<Fact, Long> {

    // All facts for an entity, ordered by key then validity start.
    List<Fact> findByEntityIdOrderByFactKeyAscValidFromAsc(Long entityId);

    // The current (open-ended) facts for an entity and key.
    List<Fact> findByEntityIdAndFactKeyAndValidToIsNull(Long entityId, String factKey);

    // All facts sourced from a given document, used by the deletion cascade
    // (ADR-026) to mark them stale.
    List<Fact> findBySourceDocId(Long sourceDocId);

    // Date-typed facts with a given key whose value falls within a window.
    // Used for cross-domain deadline queries ("everything expiring in 60 days").
    @Query("select f from Fact f where f.factKey = :factKey and f.valueDate between :from and :to "
            + "and f.validTo is null and f.verificationStatus <> 'STALE'")
    List<Fact> findExpiringBetween(
            @Param("factKey") String factKey, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
