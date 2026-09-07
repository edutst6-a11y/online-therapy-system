package com.mindcare.backend.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartConversationRequest(@NotNull UUID otherUserId) {
}
