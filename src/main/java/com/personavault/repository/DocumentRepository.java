/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.personavault.entity.Document;

/**
 * Spring Data JPA repository for {@link Document} entities.
 *
 * <p>Provides standard CRUD operations plus domain-specific queries
 * for the document management UI.
 */
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * Returns all active (non-deleted) documents, most recently ingested first.
     */
    List<Document> findByDeletedAtIsNullOrderByIngestedAtDesc();

    /**
     * Returns active documents whose filename contains the given search term (case-insensitive).
     */
    List<Document> findByDeletedAtIsNullAndFileNameContainingIgnoreCase(String searchTerm);
}
