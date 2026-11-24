/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personavault.entity.VaultEntity;

// Data access for VaultEntity (ADR-017).
public interface VaultEntityRepository extends JpaRepository<VaultEntity, Long> {

    // Finds a live (not soft-deleted) entity by type and canonical name.
    Optional<VaultEntity> findByEntityTypeAndCanonicalNameAndDeletedAtIsNull(String entityType, String canonicalName);

    // Lists all live entities of a given type, ordered by canonical name.
    List<VaultEntity> findByEntityTypeAndDeletedAtIsNullOrderByCanonicalNameAsc(String entityType);
}
