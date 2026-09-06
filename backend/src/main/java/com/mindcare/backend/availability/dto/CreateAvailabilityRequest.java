package com.mindcare.backend.availability.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateAvailabilityRequest(
        @NotNull @Future Instant startTime,
        @NotNull @Future Instant endTime
) {
}
