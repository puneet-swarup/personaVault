/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.personavault.entity.Notification;
import com.personavault.entity.Policy;
import com.personavault.repository.NotificationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private InAppNotificationChannel inAppChannel;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(List.of(inAppChannel), notificationRepository);
    }

    @Test
    @DisplayName("should create verified renewal alert with premium")
    void shouldCreateVerifiedAlert() {
        Policy policy = new Policy();
        ReflectionTestUtils.setField(policy, "id", 1L);
        policy.setPolicyType("HEALTH");
        policy.setInsurerName("HDFC Ergo");
        policy.setExpiryDate(LocalDate.of(2026, 10, 14));
        policy.setAnnualPremium(new BigDecimal("15000"));
        policy.setVerified(true);

        notificationService.createRenewalAlert(policy, 23);

        verify(inAppChannel).send(any(Notification.class));
    }

    @Test
    @DisplayName("should create unverified alert with caveat message")
    void shouldCreateUnverifiedAlert() {
        Policy policy = new Policy();
        ReflectionTestUtils.setField(policy, "id", 2L);
        policy.setPolicyType("CAR");
        policy.setInsurerName("ICICI");
        policy.setExpiryDate(LocalDate.of(2026, 11, 4));
        policy.setAnnualPremium(new BigDecimal("8500"));
        policy.setVerified(false);

        notificationService.createRenewalAlert(policy, 45);

        verify(inAppChannel).send(any(Notification.class));
    }

    @Test
    @DisplayName("should handle null premium gracefully")
    void shouldHandleNullPremium() {
        Policy policy = new Policy();
        ReflectionTestUtils.setField(policy, "id", 3L);
        policy.setPolicyType("LIFE");
        policy.setInsurerName("SBI Life");
        policy.setExpiryDate(LocalDate.of(2027, 6, 22));
        policy.setAnnualPremium(null);
        policy.setVerified(true);

        // Should not throw
        notificationService.createRenewalAlert(policy, 200);

        verify(inAppChannel).send(any(Notification.class));
    }

    @Test
    @DisplayName("getUnread should delegate to repository")
    void getUnreadDelegates() {
        Notification n = new Notification();
        when(notificationRepository.findByIsReadFalseOrderByCreatedAtDesc()).thenReturn(List.of(n));

        List<Notification> result = notificationService.getUnread();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getUnreadCount should delegate to repository")
    void getUnreadCountDelegates() {
        when(notificationRepository.countByIsReadFalse()).thenReturn(3L);

        assertThat(notificationService.getUnreadCount()).isEqualTo(3);
    }
}
