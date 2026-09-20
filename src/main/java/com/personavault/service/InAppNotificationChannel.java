/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import org.springframework.stereotype.Component;

import com.personavault.entity.Notification;
import com.personavault.repository.NotificationRepository;

/**
 * In-app notification channel: persists the notification to the database.
 *
 * <p>The dashboard and notification panel read from the {@code notifications}
 * table. This is the only channel active in v0.2.0.
 *
 * <p>This is also the <b>canonical</b> channel — even if email/SMS channels
 * are added later, the in-app record is always created first.
 */
@Component
public class InAppNotificationChannel implements NotificationChannel {

    private final NotificationRepository notificationRepository;

    public InAppNotificationChannel(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public String type() {
        return "IN_APP";
    }

    @Override
    public void send(Notification notification) {
        notificationRepository.save(notification);
    }
}
