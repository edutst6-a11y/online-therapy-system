package com.mindcare.backend.treatmentplan.dto;

import com.mindcare.backend.model.GoalStatus;
import com.mindcare.backend.model.TreatmentGoal;

import java.util.UUID;

public record GoalDto(UUID id, String description, String interventions, GoalStatus status) {
    public static GoalDto from(TreatmentGoal g) {
        return new GoalDto(g.getId(), g.getDescription(), g.getInterventions(), g.getStatus());
    }
}
