package com.mindcare.backend.intake.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record SubmitIntakeRequest(
        @Past LocalDate dateOfBirth,
        String phone,
        String emergencyContactName,
        String emergencyContactPhone,
        @Size(max = 2000) String reasonForSeekingCare,
        @AssertTrue(message = "Consent is required to continue") boolean consentGiven
) {
}
