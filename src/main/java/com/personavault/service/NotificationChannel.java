/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import com.personavault.entity.Notification;

/**
 * A notification delivery channel.
 *
 * <p>Implementations deliver notifications via a specific medium:
 * <ul>
 *   <li>{@link InAppNotificationChannel} — persists to DB, shown in dashboard (v0.2.0)</li>
 *   <li>Future: Email, SMS, Push Notification — one new class each</li>
 * </ul>
 *
 * <p>Channels are auto-discovered via Spring DI. The {@link NotificationService}
 * injects all implementations and fans out to each. Adding a new channel
 * requires zero changes to existing code — only a new {@code @Component}.
 *
 * <p>Channels can be conditionally enabled via
 * {@code @ConditionalOnProperty} (e.g., email only if SMTP is configured).
 *
 * @see "ADR-014: Notification Channel Abstraction"
 */
public interface NotificationChannel {

    /**
     * @return the channel type identifier (e.g., "IN_APP", "EMAIL", "SMS")
     */
    String type();

    /**
     * Delivers the notification to the end user via this channel.
     *
     * <p>Implementations should be idempotent — calling send() twice
     * with the same notification should not create duplicate deliveries.
     *
     * @param notification the notification to deliver
     */
    void send(Notification notification);
}
