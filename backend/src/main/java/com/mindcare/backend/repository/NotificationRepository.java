package com.mindcare.backend.repository;

import com.mindcare.backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    List<Notification> findByRecipientIdAndReadAtIsNull(UUID recipientId);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
}
