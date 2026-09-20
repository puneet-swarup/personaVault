/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.personavault.entity.Notification;

/**
 * Spring Data JPA repository for {@link Notification} entities.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Returns all unread notifications, most recent first.
     * Used by the dashboard notification panel.
     */
    List<Notification> findByIsReadFalseOrderByCreatedAtDesc();

    /**
     * Returns all notifications for a specific policy.
     */
    List<Notification> findByPolicyIdOrderByCreatedAtDesc(Long policyId);

    /**
     * Deletes all notifications for a specific policy.
     * Called during cascade deletion.
     */
    void deleteByPolicyId(Long policyId);

    /**
     * Marks a single notification as read.
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id")
    void markRead(@Param("id") Long id);

    /**
     * Counts unread notifications (for the nav badge).
     */
    long countByIsReadFalse();
}
