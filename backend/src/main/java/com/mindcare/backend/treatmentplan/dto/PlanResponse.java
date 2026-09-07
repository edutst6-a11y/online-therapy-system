package com.mindcare.backend.treatmentplan.dto;

import com.mindcare.backend.model.GoalStatus;
import com.mindcare.backend.model.TreatmentPlan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        UUID clientId,
        String clientName,
        UUID clinicianId,
        String clinicianName,
        LocalDate reviewDate,
        GoalStatus status,
        List<GoalDto> goals,
        Instant createdAt,
        Instant updatedAt
) {
    public static PlanResponse from(TreatmentPlan p) {
        return new PlanResponse(
                p.getId(),
                p.getClient().getId(),
                p.getClient().getFullName(),
                p.getResponsibleClinician().getId(),
                p.getResponsibleClinician().getFullName(),
                p.getReviewDate(),
                p.getStatus(),
                p.getGoals().stream().map(GoalDto::from).toList(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
