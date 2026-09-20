/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * An in-app notification (renewal alert, deadline reminder).
 *
 * <p>This is the canonical record for the {@code IN_APP} notification channel.
 * The dashboard and notification panel read from this table.
 *
 * <p>Future channels (email, SMS) will have their own delivery tracking
 * in a separate table (Phase 4+).
 *
 * @see NotificationChannel
 */
@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    /** Notification type: RENEWAL, DEADLINE, EXPIRED, INFO. */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    /** Short headline (e.g., "Health policy renewal approaching"). */
    @Column(name = "title", nullable = false, length = 256)
    private String title;

    /** Full message body shown in the notification panel. */
    @Column(name = "message", nullable = false)
    private String message;

    /** The policy this notification relates to (nullable for non-policy notifications). */
    @Column(name = "policy_id")
    private Long policyId;

    /** The date the renewal/deadline is due. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** The premium amount due (for renewal notifications). */
    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    /** Whether the user has seen this notification. */
    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    /** When this notification was created. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Marks this notification as read.
     */
    public void markRead() {
        this.isRead = true;
    }
}
