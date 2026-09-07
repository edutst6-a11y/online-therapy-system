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

import java.time.Instant;
import java.util.UUID;

/**
 * DRAFT notes are freely editable by their author. Once SIGNED, a note is
 * permanently immutable — the only way to correct a signed note is
 * {@code amendsNoteId} pointing at it from a brand new note, so the
 * original stays in the record exactly as it was signed.
 */
@Entity
@Table(name = "clinical_notes")
public class ClinicalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "therapist_id", nullable = false)
    private User therapist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    /** Nullable: the signed note this one corrects, forming a version chain. */
    @Column
    private UUID amendsNoteId;

    @Column(length = 100)
    private String sessionType;

    @Column(length = 2000)
    private String presentingConcerns;

    @Column(length = 2000)
    private String clinicalObservations;

    @Column(length = 2000)
    private String interventions;

    @Column(length = 2000)
    private String clientResponse;

    @Column(length = 2000)
    private String riskAssessment;

    @Column(length = 2000)
    private String plan;

    @Column(length = 2000)
    private String followUp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NoteStatus status = NoteStatus.DRAFT;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    private Instant signedAt;

    protected ClinicalNote() {
    }

    public ClinicalNote(User client, User therapist, Appointment appointment, UUID amendsNoteId) {
        this.client = client;
        this.therapist = therapist;
        this.appointment = appointment;
        this.amendsNoteId = amendsNoteId;
    }

    public UUID getId() {
        return id;
    }

    public User getClient() {
        return client;
    }

    public User getTherapist() {
        return therapist;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public UUID getAmendsNoteId() {
        return amendsNoteId;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getPresentingConcerns() {
        return presentingConcerns;
    }

    public void setPresentingConcerns(String presentingConcerns) {
        this.presentingConcerns = presentingConcerns;
    }

    public String getClinicalObservations() {
        return clinicalObservations;
    }

    public void setClinicalObservations(String clinicalObservations) {
        this.clinicalObservations = clinicalObservations;
    }

    public String getInterventions() {
        return interventions;
    }

    public void setInterventions(String interventions) {
        this.interventions = interventions;
    }

    public String getClientResponse() {
        return clientResponse;
    }

    public void setClientResponse(String clientResponse) {
        this.clientResponse = clientResponse;
    }

    public String getRiskAssessment() {
        return riskAssessment;
    }

    public void setRiskAssessment(String riskAssessment) {
        this.riskAssessment = riskAssessment;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getFollowUp() {
        return followUp;
    }

    public void setFollowUp(String followUp) {
        this.followUp = followUp;
    }

    public NoteStatus getStatus() {
        return status;
    }

    public boolean isSigned() {
        return status == NoteStatus.SIGNED;
    }

    public void sign() {
        this.status = NoteStatus.SIGNED;
        this.signedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
