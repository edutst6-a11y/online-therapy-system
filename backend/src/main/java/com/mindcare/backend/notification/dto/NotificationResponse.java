package com.mindcare.backend.notification.dto;

import com.mindcare.backend.model.Notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        UUID relatedId,
        Instant createdAt,
        Instant readAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType().name(), n.getTitle(), n.getBody(),
                n.getRelatedId(), n.getCreatedAt(), n.getReadAt()
        );
    }
}
