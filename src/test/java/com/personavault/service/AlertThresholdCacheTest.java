/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.personavault.entity.AlertThreshold;
import com.personavault.repository.AlertThresholdRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlertThresholdCache}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertThresholdCache")
class AlertThresholdCacheTest {

    @Mock
    private AlertThresholdRepository repository;

    private AlertThresholdCache cache;

    @BeforeEach
    void setUp() {
        cache = new AlertThresholdCache(repository);
        // Simulate @PostConstruct load
        when(repository.findAll())
                .thenReturn(
                        List.of(buildThreshold("HEALTH", 60), buildThreshold("CAR", 30), buildThreshold("LIFE", 90)));
        cache.load();
    }

    @Test
    @DisplayName("should return configured threshold for known type")
    void shouldReturnConfiguredThreshold() {
        assertThat(cache.getDaysBefore("HEALTH")).isEqualTo(60);
        assertThat(cache.getDaysBefore("CAR")).isEqualTo(30);
        assertThat(cache.getDaysBefore("LIFE")).isEqualTo(90);
    }

    @Test
    @DisplayName("should return default (30) for unknown type")
    void shouldReturnDefaultForUnknownType() {
        assertThat(cache.getDaysBefore("PET_INSURANCE")).isEqualTo(30);
        assertThat(cache.getDaysBefore("")).isEqualTo(30);
    }

    @Test
    @DisplayName("update should refresh cache and persist to DB")
    void updateShouldRefreshAndPersist() {
        cache.update("CAR", 45);

        assertThat(cache.getDaysBefore("CAR")).isEqualTo(45);
        verify(repository).updateDaysBefore("CAR", 45);
    }

    private AlertThreshold buildThreshold(String type, int days) {
        AlertThreshold t = new AlertThreshold();
        t.setPolicyType(type);
        t.setDaysBefore(days);
        return t;
    }
}
