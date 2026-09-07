package com.mindcare.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One per client. Created/updated by the client themselves during onboarding. */
@Entity
@Table(name = "client_intakes")
public class ClientIntake {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private User client;

    private LocalDate dateOfBirth;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String emergencyContactName;

    @Column(length = 30)
    private String emergencyContactPhone;

    @Column(length = 2000)
    private String reasonForSeekingCare;

    @Column(nullable = false)
    private boolean consentGiven = false;

    private Instant consentGivenAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected ClientIntake() {
    }

    public ClientIntake(User client) {
        this.client = client;
    }

    public UUID getId() {
        return id;
    }

    public User getClient() {
        return client;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public String getReasonForSeekingCare() {
        return reasonForSeekingCare;
    }

    public void setReasonForSeekingCare(String reasonForSeekingCare) {
        this.reasonForSeekingCare = reasonForSeekingCare;
    }

    public boolean isConsentGiven() {
        return consentGiven;
    }

    public void setConsentGiven(boolean consentGiven) {
        this.consentGiven = consentGiven;
        this.consentGivenAt = consentGiven ? Instant.now() : null;
    }

    public Instant getConsentGivenAt() {
        return consentGivenAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
