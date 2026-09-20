/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.personavault.repository.AlertThresholdRepository;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * In-memory cache of alert thresholds, loaded from the database at startup.
 *
 * <p>Avoids a DB query on every scheduled renewal check. The cache is
 * a {@link ConcurrentHashMap} — safe for concurrent reads from the
 * scheduler thread without synchronization.
 *
 * <p>Updated via {@link #update(String, int)} when the user changes
 * a threshold (Phase 3 UI). The DB write and cache update are in
 * the same transaction for consistency.
 *
 * @see "ADR-012: Configurable Alert Thresholds"
 */
@Slf4j
@Component
public class AlertThresholdCache {

    private static final int DEFAULT_DAYS = 30;

    private final AlertThresholdRepository repository;
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    public AlertThresholdCache(AlertThresholdRepository repository) {
        this.repository = repository;
    }

    /**
     * Loads all thresholds from the database into the in-memory cache.
     * Called once at application startup.
     */
    @PostConstruct
    void load() {
        repository.findAll().forEach(t -> cache.put(t.getPolicyType(), t.getDaysBefore()));
        log.info("Loaded {} alert thresholds: {}", cache.size(), cache);
    }

    /**
     * Returns the number of days before expiry to alert for the given policy type.
     *
     * @param policyType the policy type (e.g., "HEALTH", "CAR")
     * @return the configured threshold, or {@value #DEFAULT_DAYS} for unknown types
     */
    public int getDaysBefore(String policyType) {
        return cache.getOrDefault(policyType, DEFAULT_DAYS);
    }

    /**
     * Updates a threshold in both the database and the in-memory cache.
     *
     * @param policyType the policy type
     * @param daysBefore the new threshold in days
     */
    public void update(String policyType, int daysBefore) {
        repository.updateDaysBefore(policyType, daysBefore);
        cache.put(policyType, daysBefore);
        log.info("Updated alert threshold: {} → {} days", policyType, daysBefore);
    }
}
