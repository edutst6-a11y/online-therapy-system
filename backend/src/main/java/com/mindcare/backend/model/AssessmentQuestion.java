package com.mindcare.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "assessment_questions")
public class AssessmentQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private AssessmentTemplate template;

    @Column(nullable = false)
    private int orderIndex;

    @Column(nullable = false, length = 500)
    private String questionText;

    @Column(nullable = false)
    private int minScore;

    @Column(nullable = false)
    private int maxScore;

    protected AssessmentQuestion() {
    }

    public AssessmentQuestion(AssessmentTemplate template, int orderIndex, String questionText, int minScore, int maxScore) {
        this.template = template;
        this.orderIndex = orderIndex;
        this.questionText = questionText;
        this.minScore = minScore;
        this.maxScore = maxScore;
    }

    public UUID getId() {
        return id;
    }

    public AssessmentTemplate getTemplate() {
        return template;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public String getQuestionText() {
        return questionText;
    }

    public int getMinScore() {
        return minScore;
    }

    public int getMaxScore() {
        return maxScore;
    }
}
