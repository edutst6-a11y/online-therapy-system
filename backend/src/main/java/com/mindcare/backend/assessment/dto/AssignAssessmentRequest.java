package com.mindcare.backend.assessment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignAssessmentRequest(
        @NotNull UUID templateId,
        @NotNull UUID clientId
) {
}
