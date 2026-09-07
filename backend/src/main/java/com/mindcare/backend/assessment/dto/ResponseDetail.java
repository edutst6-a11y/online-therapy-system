package com.mindcare.backend.assessment.dto;

import com.mindcare.backend.model.AssessmentAnswer;
import com.mindcare.backend.model.AssessmentResponse;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record ResponseDetail(
        UUID id,
        UUID templateId,
        String templateName,
        String templateDescription,
        UUID clientId,
        String clientName,
        Instant assignedAt,
        Instant completedAt,
        Integer totalScore,
        String interpretation,
        List<QuestionDto> questions,
        Map<UUID, Integer> answers
) {
    public static ResponseDetail from(AssessmentResponse r) {
        Map<UUID, Integer> answerMap = r.getAnswers().stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), AssessmentAnswer::getScore));

        return new ResponseDetail(
                r.getId(),
                r.getTemplate().getId(),
                r.getTemplate().getName(),
                r.getTemplate().getDescription(),
                r.getClient().getId(),
                r.getClient().getFullName(),
                r.getAssignedAt(),
                r.getCompletedAt(),
                r.getTotalScore(),
                r.getInterpretation(),
                r.getTemplate().getQuestions().stream().map(QuestionDto::from).toList(),
                answerMap
        );
    }
}
