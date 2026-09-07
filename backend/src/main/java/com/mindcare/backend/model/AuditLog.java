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
 * WHO / WHAT / WHEN / WHICH RECORD / FROM WHERE / RESULT, per the blueprint's audit
 * spec. Append-only: nothing in this codebase ever updates or deletes a row here.
 * actorName is a snapshot at write time so the record stays legible even if the
 * actor field's user is later disabled or renamed.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false)
    private String actorName;

    @Column(nullable = false)
    private String action;

    private String recordType;

    private String recordId;

    @Column(nullable = false)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditResult result;

    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {
    }

    public AuditLog(User actor, String actorName, String action, String recordType, String recordId,
                     String ipAddress, AuditResult result, String detail) {
        this.actor = actor;
        this.actorName = actorName;
        this.action = action;
        this.recordType = recordType;
        this.recordId = recordId;
        this.ipAddress = ipAddress;
        this.result = result;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public User getActor() {
        return actor;
    }

    public String getActorName() {
        return actorName;
    }

    public String getAction() {
        return action;
    }

    public String getRecordType() {
        return recordType;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public AuditResult getResult() {
        return result;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
