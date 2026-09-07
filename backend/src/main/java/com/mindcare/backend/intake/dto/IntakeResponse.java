package com.mindcare.backend.intake.dto;

import com.mindcare.backend.model.ClientIntake;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record IntakeResponse(
        UUID id,
        UUID clientId,
        String clientName,
        LocalDate dateOfBirth,
        String phone,
        String emergencyContactName,
        String emergencyContactPhone,
        String reasonForSeekingCare,
        boolean consentGiven,
        Instant consentGivenAt,
        Instant updatedAt
) {
    public static IntakeResponse from(ClientIntake intake) {
        return new IntakeResponse(
                intake.getId(),
                intake.getClient().getId(),
                intake.getClient().getFullName(),
                intake.getDateOfBirth(),
                intake.getPhone(),
                intake.getEmergencyContactName(),
                intake.getEmergencyContactPhone(),
                intake.getReasonForSeekingCare(),
                intake.isConsentGiven(),
                intake.getConsentGivenAt(),
                intake.getUpdatedAt()
        );
    }
}
