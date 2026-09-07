package com.mindcare.backend.notification;

import com.mindcare.backend.model.Notification;
import com.mindcare.backend.model.NotificationType;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Central point for raising notifications, so every part of the system that
 * needs to tell a user something (a new appointment, a message, an assigned
 * assessment) writes through the same place rather than each feature growing
 * its own ad-hoc notification logic.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void notify(User recipient, NotificationType type, String title, String body, UUID relatedId) {
        notificationRepository.save(new Notification(recipient, type, title, body, relatedId));
    }
}
