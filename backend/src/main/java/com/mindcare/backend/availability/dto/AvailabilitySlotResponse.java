package com.mindcare.backend.availability.dto;

import com.mindcare.backend.model.AvailabilitySlot;

import java.time.Instant;
import java.util.UUID;

public record AvailabilitySlotResponse(
        UUID id,
        UUID therapistId,
        String therapistName,
        Instant startTime,
        Instant endTime,
        boolean booked
) {
    public static AvailabilitySlotResponse from(AvailabilitySlot slot) {
        return new AvailabilitySlotResponse(
                slot.getId(),
                slot.getTherapist().getId(),
                slot.getTherapist().getFullName(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.isBooked()
        );
    }
}
