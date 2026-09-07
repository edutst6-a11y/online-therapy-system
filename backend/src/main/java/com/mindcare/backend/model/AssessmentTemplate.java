package com.mindcare.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A reusable, versionable questionnaire definition — the "assessment engine"
 * from the blueprint, so new validated instruments can be added without any
 * schema change. scoreBands encodes interpretation rules as
 * "min-max:Label;min-max:Label;..." (e.g. "0-4:Minimal;5-9:Mild").
 */
@Entity
@Table(name = "assessment_templates")
public class AssessmentTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false, length = 500)
    private String scoreBands;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<AssessmentQuestion> questions = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AssessmentTemplate() {
    }

    public AssessmentTemplate(String name, String description, String scoreBands) {
        this.name = name;
        this.description = description;
        this.scoreBands = scoreBands;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getVersion() {
        return version;
    }

    public String getScoreBands() {
        return scoreBands;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<AssessmentQuestion> getQuestions() {
        return questions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Looks up the interpretation label for a total score from scoreBands. */
    public String interpret(int totalScore) {
        for (String band : scoreBands.split(";")) {
            String[] parts = band.split(":");
            if (parts.length != 2) continue;
            String[] range = parts[0].split("-");
            if (range.length != 2) continue;
            int min = Integer.parseInt(range[0].trim());
            int max = Integer.parseInt(range[1].trim());
            if (totalScore >= min && totalScore <= max) {
                return parts[1].trim();
            }
        }
        return "Unscored";
    }
}
