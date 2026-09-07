package com.mindcare.backend.clinicalnote.dto;

import jakarta.validation.constraints.Size;

/** Shared field set for creating and updating a draft note. */
public record NoteContent(
        @Size(max = 100) String sessionType,
        @Size(max = 2000) String presentingConcerns,
        @Size(max = 2000) String clinicalObservations,
        @Size(max = 2000) String interventions,
        @Size(max = 2000) String clientResponse,
        @Size(max = 2000) String riskAssessment,
        @Size(max = 2000) String plan,
        @Size(max = 2000) String followUp
) {
}
