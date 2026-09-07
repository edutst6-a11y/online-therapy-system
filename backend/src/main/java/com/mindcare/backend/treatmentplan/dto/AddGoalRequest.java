package com.mindcare.backend.treatmentplan.dto;

import jakarta.validation.constraints.NotBlank;

public record AddGoalRequest(
        @NotBlank String description,
        String interventions
) {
}
