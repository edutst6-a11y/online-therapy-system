package com.mindcare.backend.treatmentplan.dto;

import com.mindcare.backend.model.GoalStatus;

/** Partial update — only non-null fields are applied. */
public record UpdateGoalRequest(
        String description,
        String interventions,
        GoalStatus status
) {
}
