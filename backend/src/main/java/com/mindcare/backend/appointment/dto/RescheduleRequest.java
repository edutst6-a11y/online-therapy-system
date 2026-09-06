package com.mindcare.backend.appointment.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleRequest(
        @NotNull @Future Instant newScheduledAt
) {
}
