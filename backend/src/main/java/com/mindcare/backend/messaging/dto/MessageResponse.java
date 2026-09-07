package com.mindcare.backend.messaging.dto;

import com.mindcare.backend.model.Message;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID senderId,
        String senderName,
        String body,
        Instant sentAt,
        Instant readAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(m.getId(), m.getSender().getId(), m.getSender().getFullName(), m.getBody(), m.getSentAt(), m.getReadAt());
    }
}
