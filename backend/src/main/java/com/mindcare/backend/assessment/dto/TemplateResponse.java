package com.mindcare.backend.assessment.dto;

import com.mindcare.backend.model.AssessmentTemplate;

import java.util.List;
import java.util.UUID;

public record TemplateResponse(
        UUID id,
        String name,
        String description,
        int version,
        boolean active,
        List<QuestionDto> questions
) {
    public static TemplateResponse from(AssessmentTemplate t) {
        return new TemplateResponse(
                t.getId(), t.getName(), t.getDescription(), t.getVersion(), t.isActive(),
                t.getQuestions().stream().map(QuestionDto::from).toList()
        );
    }
}
