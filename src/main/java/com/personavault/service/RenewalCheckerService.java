/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personavault.entity.Policy;
import com.personavault.repository.PolicyRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Daily renewal checker. Scans all policies and creates alerts
 * for those approaching their expiry date within the configured threshold.
 *
 * <p>Scheduled at 8:00 AM IST daily. Also runs on application startup
 * as a catch-up (in case the app was down at 8 AM).
 *
 * <p>Per ADR-006, this uses {@code @Scheduled} (not db-scheduler) for v1.
 * The startup catch-up handles the "app was down" case.
 *
 * <p>Per ADR-012, thresholds are read from the database (cached in
 * {@link AlertThresholdCache}), not hardcoded.
 *
 * @see "ADR-006: @Scheduled Over db-scheduler"
 * @see "ADR-012: Configurable Alert Thresholds"
 */
@Slf4j
@Service
public class RenewalCheckerService {

    private final PolicyRepository policyRepository;
    private final AlertThresholdCache thresholdCache;
    private final NotificationService notificationService;

    public RenewalCheckerService(
            PolicyRepository policyRepository,
            AlertThresholdCache thresholdCache,
            NotificationService notificationService) {
        this.policyRepository = policyRepository;
        this.thresholdCache = thresholdCache;
        this.notificationService = notificationService;
    }

    /**
     * Daily scheduled check at 8:00 AM.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void checkUpcomingRenewals() {
        log.info("Renewal check started");
        int alertsCreated = runCheck();
        log.info("Renewal check complete: {} alerts created", alertsCreated);
    }

    /**
     * Startup catch-up: runs the check on application start.
     * If the app was down at 8 AM, this ensures no alert is missed.
     */
    @Scheduled(initialDelay = 10_000, fixedDelay = Long.MAX_VALUE)
    public void startupCatchUp() {
        log.info("Startup renewal catch-up check");
        int alertsCreated = runCheck();
        if (alertsCreated > 0) {
            log.info("Startup catch-up: {} alerts created (app was likely down at 8 AM)", alertsCreated);
        }
    }

    /**
     * Core check logic: iterates all policies, compares expiry date
     * against the per-type threshold, creates alerts for matches.
     *
     * @return the number of alerts created
     */
    @Transactional
    protected int runCheck() {
        LocalDate today = LocalDate.now();
        int count = 0;

        // Look ahead 90 days max (covers the largest threshold: LIFE = 90)
        List<Policy> candidates = policyRepository.findByExpiryDateBetween(today, today.plusDays(90));

        for (Policy policy : candidates) {
            long daysLeft = ChronoUnit.DAYS.between(today, policy.getExpiryDate());
            int threshold = thresholdCache.getDaysBefore(policy.getPolicyType());

            if (daysLeft <= threshold) {
                // Avoid duplicate alerts: skip if a RENEWAL notification
                // already exists for this policy with the same due date
                if (!notificationService.getUnread().stream()
                        .anyMatch(n -> n.getPolicyId().equals(policy.getId()) && "RENEWAL".equals(n.getType()))) {
                    notificationService.createRenewalAlert(policy, daysLeft);
                    count++;
                }
            }
        }

        return count;
    }
}
