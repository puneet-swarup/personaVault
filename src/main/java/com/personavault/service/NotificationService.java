/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personavault.entity.Notification;
import com.personavault.entity.Policy;
import com.personavault.repository.NotificationRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates notification creation and dispatch.
 *
 * <p>Fans out to all active {@link NotificationChannel} implementations.
 * In v0.2.0, only {@link InAppNotificationChannel} is active.
 *
 * <p>The message content adapts based on the policy's {@code verified} flag:
 * <ul>
 *   <li>Verified: "Expires in 23 days (14 Oct)"</li>
 *   <li>Unverified: "⚠️ May expire in 23 days (14 Oct). Date is auto-extracted — please verify."</li>
 * </ul>
 */
@Slf4j
@Service
public class NotificationService {

    private final List<NotificationChannel> channels;
    private final NotificationRepository notificationRepository;

    /**
     * Constructor injection. Spring auto-injects all NotificationChannel beans.
     *
     * @param channels               all active notification channels
     * @param notificationRepository for query operations (markRead, count, etc.)
     */
    public NotificationService(List<NotificationChannel> channels, NotificationRepository notificationRepository) {
        this.channels = channels;
        this.notificationRepository = notificationRepository;
    }

    /**
     * Creates a renewal notification and dispatches it through all channels.
     *
     * @param policy  the policy that's expiring
     * @param daysLeft number of days until expiry
     */
    @Transactional
    public void createRenewalAlert(Policy policy, long daysLeft) {
        String title;
        String message;

        if (policy.isVerified()) {
            title = policy.getPolicyType() + " policy renewal — " + daysLeft + " days";
            message = policy.getInsurerName() + " " + policy.getPolicyType()
                    + " policy expires in " + daysLeft + " days (" + policy.getExpiryDate() + ")"
                    + (policy.getAnnualPremium() != null
                            ? ". Premium: ₹" + policy.getAnnualPremium().toPlainString()
                            : "");
        } else {
            title = "⚠️ " + policy.getPolicyType() + " policy may renew — " + daysLeft + " days";
            message = policy.getInsurerName() + " " + policy.getPolicyType()
                    + " policy may expire in " + daysLeft + " days (" + policy.getExpiryDate() + ")"
                    + ". Date is auto-extracted — please verify on the dashboard.";
        }

        Notification notification = new Notification();
        notification.setType("RENEWAL");
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setPolicyId(policy.getId());
        notification.setDueDate(policy.getExpiryDate());
        notification.setAmount(policy.getAnnualPremium());
        notification.setCreatedAt(Instant.now());

        // Fan out to all channels
        for (NotificationChannel channel : channels) {
            channel.send(notification);
        }

        log.info(
                "Renewal alert created: {} ({} days left, verified={})",
                policy.getPolicyNumber(),
                daysLeft,
                policy.isVerified());
    }

    /**
     * Marks a notification as read.
     */
    public void markRead(Long id) {
        notificationRepository.markRead(id);
    }

    /**
     * Returns all unread notifications for the dashboard panel.
     */
    public List<Notification> getUnread() {
        return notificationRepository.findByIsReadFalseOrderByCreatedAtDesc();
    }

    /**
     * Returns the count of unread notifications (for the nav badge).
     */
    public long getUnreadCount() {
        return notificationRepository.countByIsReadFalse();
    }
}
