package com.mindcare.backend.messaging.dto;

import com.mindcare.backend.model.Conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(
        UUID id,
        UUID clientId,
        String clientName,
        UUID therapistId,
        String therapistName,
        Instant createdAt,
        long unreadCount
) {
    public static ConversationSummary from(Conversation c, long unreadCount) {
        return new ConversationSummary(
                c.getId(), c.getClient().getId(), c.getClient().getFullName(),
                c.getTherapist().getId(), c.getTherapist().getFullName(),
                c.getCreatedAt(), unreadCount
        );
    }
}
