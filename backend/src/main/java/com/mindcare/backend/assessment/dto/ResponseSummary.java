package com.mindcare.backend.assessment.dto;

import com.mindcare.backend.model.AssessmentResponse;

import java.time.Instant;
import java.util.UUID;

public record ResponseSummary(
        UUID id,
        UUID templateId,
        String templateName,
        UUID clientId,
        String clientName,
        String assignedByName,
        Instant assignedAt,
        Instant completedAt,
        Integer totalScore,
        String interpretation
) {
    public static ResponseSummary from(AssessmentResponse r) {
        return new ResponseSummary(
                r.getId(),
                r.getTemplate().getId(),
                r.getTemplate().getName(),
                r.getClient().getId(),
                r.getClient().getFullName(),
                r.getAssignedBy().getFullName(),
                r.getAssignedAt(),
                r.getCompletedAt(),
                r.getTotalScore(),
                r.getInterpretation()
        );
    }
}
