package com.mindcare.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "treatment_goals")
public class TreatmentGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private TreatmentPlan plan;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(length = 1000)
    private String interventions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoalStatus status = GoalStatus.ACTIVE;

    protected TreatmentGoal() {
    }

    public TreatmentGoal(TreatmentPlan plan, String description, String interventions) {
        this.plan = plan;
        this.description = description;
        this.interventions = interventions;
    }

    public UUID getId() {
        return id;
    }

    public TreatmentPlan getPlan() {
        return plan;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInterventions() {
        return interventions;
    }

    public void setInterventions(String interventions) {
        this.interventions = interventions;
    }

    public GoalStatus getStatus() {
        return status;
    }

    public void setStatus(GoalStatus status) {
        this.status = status;
    }
}
