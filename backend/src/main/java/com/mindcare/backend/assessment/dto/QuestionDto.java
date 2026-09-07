package com.mindcare.backend.assessment.dto;

import com.mindcare.backend.model.AssessmentQuestion;

import java.util.UUID;

public record QuestionDto(UUID id, int orderIndex, String questionText, int minScore, int maxScore) {
    public static QuestionDto from(AssessmentQuestion q) {
        return new QuestionDto(q.getId(), q.getOrderIndex(), q.getQuestionText(), q.getMinScore(), q.getMaxScore());
    }
}
