/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.personavault.entity.Fact;
import com.personavault.entity.VaultEntity;
import com.personavault.repository.FactRepository;
import com.personavault.repository.VaultEntityRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests for FactService, focused on the ADR-021 provenance rule and the
// ADR-017 entity lifecycle.
@ExtendWith(MockitoExtension.class)
@DisplayName("FactService")
class FactServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-10-29T10:00:00Z");

    @Mock
    private FactRepository factRepository;

    @Mock
    private VaultEntityRepository entityRepository;

    private FactService factService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        factService = new FactService(factRepository, entityRepository, fixedClock);
    }

    @Test
    @DisplayName("rejects a fact with no source document (ADR-021)")
    void recordFactWithoutSourceThrowsProvenanceRequired() {
        Fact fact = new Fact();
        fact.setEntityId(1L);
        fact.setFactKey("expiryDate");
        fact.setValueType("DATE");
        fact.setExtractedBy("LLM");

        assertThatThrownBy(() -> factService.recordFact(fact))
                .isInstanceOf(FactService.ProvenanceRequiredException.class)
                .hasMessageContaining("provenance is mandatory");

        verify(factRepository, never()).save(any());
    }

    @Test
    @DisplayName("applies defaults for confidence, status and timestamp")
    void recordFactAppliesDefaults() {
        Fact fact = new Fact();
        fact.setEntityId(7L);
        fact.setFactKey("annualPremium");
        fact.setValueType("MONEY");
        fact.setExtractedBy("LLM");
        fact.setSourceDocId(42L);
        when(factRepository.save(any(Fact.class))).thenAnswer(inv -> inv.getArgument(0));

        Fact saved = factService.recordFact(fact);

        assertThat(saved.getConfidence()).isEqualByComparingTo(new BigDecimal("0.75"));
        assertThat(saved.getVerificationStatus()).isEqualTo("EXTRACTED");
        assertThat(saved.getFirstSeenAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    @DisplayName("keeps an explicitly supplied confidence")
    void recordFactKeepsExplicitConfidence() {
        Fact fact = new Fact();
        fact.setEntityId(7L);
        fact.setFactKey("policyNumber");
        fact.setValueType("STRING");
        fact.setExtractedBy("REGEX");
        fact.setSourceDocId(42L);
        fact.setConfidence(new BigDecimal("0.95"));
        when(factRepository.save(any(Fact.class))).thenAnswer(inv -> inv.getArgument(0));

        Fact saved = factService.recordFact(fact);

        assertThat(saved.getConfidence()).isEqualByComparingTo(new BigDecimal("0.95"));
    }

    @Test
    @DisplayName("rejects a fact with a blank fact key")
    void recordFactBlankKeyThrows() {
        Fact fact = new Fact();
        fact.setEntityId(1L);
        fact.setFactKey("  ");
        fact.setValueType("STRING");
        fact.setExtractedBy("LLM");
        fact.setSourceDocId(9L);

        assertThatThrownBy(() -> factService.recordFact(fact)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("findOrCreateEntity returns an existing entity unchanged")
    void findOrCreateEntityReturnsExisting() {
        VaultEntity existing = new VaultEntity();
        existing.setEntityType("POLICY");
        existing.setCanonicalName("POL-1");
        when(entityRepository.findByEntityTypeAndCanonicalNameAndDeletedAtIsNull("POLICY", "POL-1"))
                .thenReturn(Optional.of(existing));

        VaultEntity result = factService.findOrCreateEntity("POLICY", "POL-1", "insurance-policy");

        assertThat(result).isSameAs(existing);
        verify(entityRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreateEntity creates and stamps a new entity")
    void findOrCreateEntityCreatesMissing() {
        when(entityRepository.findByEntityTypeAndCanonicalNameAndDeletedAtIsNull("POLICY", "POL-2"))
                .thenReturn(Optional.empty());
        when(entityRepository.save(any(VaultEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        VaultEntity result = factService.findOrCreateEntity("POLICY", "POL-2", "insurance-policy");

        assertThat(result.getEntityType()).isEqualTo("POLICY");
        assertThat(result.getCanonicalName()).isEqualTo("POL-2");
        assertThat(result.getSchemaId()).isEqualTo("insurance-policy");
        assertThat(result.getCreatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(result.getUpdatedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    @DisplayName("findOrCreateEntity rejects a blank canonical name")
    void findOrCreateEntityBlankNameThrows() {
        assertThatThrownBy(() -> factService.findOrCreateEntity("POLICY", "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
