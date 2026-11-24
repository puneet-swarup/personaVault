/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.personavault.entity.WikiPage;
import com.personavault.entity.WikiPageType;

// Data access for the wiki mirror table (ADR-022). Reads here are for listing and
// search only; the markdown files remain the source of truth for page content.
public interface WikiPageRepository extends JpaRepository<WikiPage, String> {

    // All pages of one type, ordered for stable index generation.
    List<WikiPage> findByPageTypeOrderByTitleAsc(WikiPageType pageType);

    // All pages, ordered by type then title so the generated index.md is stable.
    List<WikiPage> findAllByOrderByPageTypeAscTitleAsc();

    // The page mirroring a given vault entity, if one has been compiled.
    List<WikiPage> findByEntityId(Long entityId);
}
