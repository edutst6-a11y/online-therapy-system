package com.mindcare.backend.appointment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BookAppointmentRequest(
        @NotNull UUID availabilitySlotId,
        String notes
) {
}
