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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/** One per client-therapist pair. Not a general chat — tied to the care relationship. */
@Entity
@Table(name = "conversations", uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "therapist_id"}))
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "therapist_id", nullable = false)
    private User therapist;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Conversation() {
    }

    public Conversation(User client, User therapist) {
        this.client = client;
        this.therapist = therapist;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
