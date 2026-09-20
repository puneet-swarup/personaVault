/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.personavault.entity.Policy;
import com.personavault.repository.PolicyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RenewalCheckerService}.
 *
 * <p>Tests the core check logic via the protected {@code runCheck()} method.
 * The scheduler annotations are not tested (they're Spring framework behavior).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RenewalCheckerService")
class RenewalCheckerServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private AlertThresholdCache thresholdCache;

    @Mock
    private NotificationService notificationService;

    private RenewalCheckerService renewalChecker;

    @BeforeEach
    void setUp() {
        renewalChecker = new RenewalCheckerService(policyRepository, thresholdCache, notificationService);
    }

    @Nested
    @DisplayName("Renewal Check Logic")
    class RenewalCheck {

        @Test
        @DisplayName("should create alert when policy is within threshold")
        void shouldCreateAlertWhenWithinThreshold() {
            Policy policy = buildPolicy("HEALTH", LocalDate.now().plusDays(30));
            when(policyRepository.findByExpiryDateBetween(any(), any())).thenReturn(List.of(policy));
            when(thresholdCache.getDaysBefore("HEALTH")).thenReturn(60);
            when(notificationService.getUnread()).thenReturn(List.of());

            int count = renewalChecker.runCheck();

            assertThat(count).isEqualTo(1);
            verify(notificationService).createRenewalAlert(policy, 30);
        }

        @Test
        @DisplayName("should NOT create alert when policy is beyond threshold")
        void shouldNotCreateAlertWhenBeyondThreshold() {
            Policy policy = buildPolicy("HEALTH", LocalDate.now().plusDays(90));
            when(policyRepository.findByExpiryDateBetween(any(), any())).thenReturn(List.of(policy));
            when(thresholdCache.getDaysBefore(anyString())).thenReturn(60);

            int count = renewalChecker.runCheck();

            assertThat(count).isEqualTo(0);
            verify(notificationService, never()).createRenewalAlert(any(), anyLong());
        }

        @Test
        @DisplayName("should NOT create duplicate alert if one already exists")
        void shouldNotCreateDuplicateAlert() {
            Policy policy = buildPolicy("CAR", LocalDate.now().plusDays(15));
            when(policyRepository.findByExpiryDateBetween(any(), any())).thenReturn(List.of(policy));
            when(thresholdCache.getDaysBefore("CAR")).thenReturn(30);

            // Simulate an existing unread RENEWAL notification for this policy
            var existingNotification = new com.personavault.entity.Notification();
            existingNotification.setPolicyId(policy.getId());
            existingNotification.setType("RENEWAL");
            when(notificationService.getUnread()).thenReturn(List.of(existingNotification));

            int count = renewalChecker.runCheck();

            assertThat(count).isEqualTo(0);
            verify(notificationService, never()).createRenewalAlert(any(), anyLong());
        }

        @Test
        @DisplayName("should handle empty policy list gracefully")
        void shouldHandleEmptyList() {
            when(policyRepository.findByExpiryDateBetween(any(), any())).thenReturn(List.of());

            int count = renewalChecker.runCheck();

            assertThat(count).isEqualTo(0);
            verify(notificationService, never()).createRenewalAlert(any(), anyLong());
        }

        @Test
        @DisplayName("should use default threshold (30) for unknown policy type")
        void shouldUseDefaultThresholdForUnknownType() {
            Policy policy = buildPolicy("PET_INSURANCE", LocalDate.now().plusDays(20));
            when(policyRepository.findByExpiryDateBetween(any(), any())).thenReturn(List.of(policy));
            when(thresholdCache.getDaysBefore("PET_INSURANCE")).thenReturn(30);
            when(notificationService.getUnread()).thenReturn(List.of());

            int count = renewalChecker.runCheck();

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle multiple policies correctly")
        void shouldHandleMultiplePolicies() {
            Policy health = buildPolicy("HEALTH", LocalDate.now().plusDays(20));
            Policy car = buildPolicy("CAR", LocalDate.now().plusDays(10));
            Policy life = buildPolicy("LIFE", LocalDate.now().plusDays(85));

            when(policyRepository.findByExpiryDateBetween(any(), any())).thenReturn(List.of(health, car, life));
            when(thresholdCache.getDaysBefore("HEALTH")).thenReturn(60);
            when(thresholdCache.getDaysBefore("CAR")).thenReturn(30);
            when(thresholdCache.getDaysBefore("LIFE")).thenReturn(90);
            when(notificationService.getUnread()).thenReturn(List.of());

            int count = renewalChecker.runCheck();

            // All three are within their respective thresholds
            assertThat(count).isEqualTo(3);
        }
    }

    // --- Helper ---

    private Policy buildPolicy(String type, LocalDate expiry) {
        Policy p = new Policy();
        ReflectionTestUtils.setField(p, "id", 1L);
        p.setPolicyType(type);
        p.setExpiryDate(expiry);
        p.setPolicyNumber(type + "-TEST-001");
        p.setInsurerName("Test Insurer");
        p.setVerified(true);
        return p;
    }
}
