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
@Table(name = "assessment_answers")
public class AssessmentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    private AssessmentResponse response;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private AssessmentQuestion question;

    @Column(nullable = false)
    private int score;

    protected AssessmentAnswer() {
    }

    public AssessmentAnswer(AssessmentResponse response, AssessmentQuestion question, int score) {
        this.response = response;
        this.question = question;
        this.score = score;
    }

    public UUID getId() {
        return id;
    }

    public AssessmentResponse getResponse() {
        return response;
    }

    public AssessmentQuestion getQuestion() {
        return question;
    }

    public int getScore() {
        return score;
    }
}
