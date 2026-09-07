package com.mindcare.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One assignment of a template to a client. Pending until submitted. */
@Entity
@Table(name = "assessment_responses")
public class AssessmentResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private AssessmentTemplate template;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_id", nullable = false)
    private User assignedBy;

    @Column(nullable = false, updatable = false)
    private Instant assignedAt = Instant.now();

    private Instant completedAt;

    private Integer totalScore;

    @Column(length = 100)
    private String interpretation;

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AssessmentAnswer> answers = new ArrayList<>();

    protected AssessmentResponse() {
    }

    public AssessmentResponse(AssessmentTemplate template, User client, User assignedBy) {
        this.template = template;
        this.client = client;
        this.assignedBy = assignedBy;
    }

    public UUID getId() {
        return id;
    }

    public AssessmentTemplate getTemplate() {
        return template;
    }

    public User getClient() {
        return client;
    }

    public User getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public String getInterpretation() {
        return interpretation;
    }

    public List<AssessmentAnswer> getAnswers() {
        return answers;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public void complete(int totalScore, String interpretation) {
        this.totalScore = totalScore;
        this.interpretation = interpretation;
        this.completedAt = Instant.now();
    }
}
