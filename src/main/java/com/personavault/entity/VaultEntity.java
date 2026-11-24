/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// A thing the vault knows about: a person, policy, insurer, account, provider,
// medication, condition, property, vehicle, event, or anything else.
// Per ADR-017 this replaces the single-purpose policies table. The entity type
// distinguishes domains; the actual values live in Fact rows attached to the
// entity. This is the generic half of the umbrella model: one shape, many
// domains (polymorphism at the data layer). Named VaultEntity rather than
// Entity to avoid colliding with jakarta.persistence.Entity.
@Getter
@Setter
@Entity
@Table(name = "entity")
public class VaultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    // Domain type: PERSON, POLICY, INSURER, ACCOUNT, PROVIDER, MEDICATION, ...
    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    // Normalised display name used for matching and display.
    @Column(name = "canonical_name", nullable = false, length = 255)
    private String canonicalName;

    // Which extraction schema produced this entity (ADR-018).
    @Column(name = "schema_id", length = 60)
    private String schemaId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
