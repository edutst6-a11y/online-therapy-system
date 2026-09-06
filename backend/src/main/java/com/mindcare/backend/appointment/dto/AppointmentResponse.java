package com.mindcare.backend.appointment.dto;

import com.mindcare.backend.model.Appointment;
import com.mindcare.backend.model.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID clientId,
        String clientName,
        UUID therapistId,
        String therapistName,
        Instant scheduledAt,
        int durationMinutes,
        AppointmentStatus status,
        String notes,
        String meetLink,
        Instant createdAt,
        Instant updatedAt
) {
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getClient().getId(),
                appointment.getClient().getFullName(),
                appointment.getTherapist().getId(),
                appointment.getTherapist().getFullName(),
                appointment.getScheduledAt(),
                appointment.getDurationMinutes(),
                appointment.getStatus(),
                appointment.getNotes(),
                appointment.getMeetLink(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt()
        );
    }
}
