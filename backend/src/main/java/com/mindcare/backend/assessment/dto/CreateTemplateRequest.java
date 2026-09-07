package com.mindcare.backend.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateTemplateRequest(
        @NotBlank String name,
        String description,
        @NotBlank(message = "scoreBands is required, e.g. \"0-4:Minimal;5-9:Mild\"") String scoreBands,
        @NotEmpty @Valid List<QuestionInput> questions
) {
    public record QuestionInput(
            @NotBlank String questionText,
            @NotNull Integer minScore,
            @NotNull Integer maxScore
    ) {
    }
}
