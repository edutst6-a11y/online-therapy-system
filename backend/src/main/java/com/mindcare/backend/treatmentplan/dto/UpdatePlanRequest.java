package com.mindcare.backend.treatmentplan.dto;

import com.mindcare.backend.model.GoalStatus;

import java.time.LocalDate;

/** Partial update — only non-null fields are applied. */
public record UpdatePlanRequest(
        LocalDate reviewDate,
        GoalStatus status
) {
}
