package com.mindcare.backend.treatmentplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreatePlanRequest(
        @NotNull UUID clientId,
        LocalDate reviewDate,
        @Valid List<GoalInput> goals
) {
    public record GoalInput(
            @NotBlank String description,
            String interventions
    ) {
    }
}
