package com.mindcare.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * A super admin can switch which role they operate the system as (activeRole)
     * without their real underlying role ever changing, and can grant super-admin
     * status to other accounts. Ordinary users never have this set.
     */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean superAdmin = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Role activeRole;

    @Column(nullable = false, columnDefinition = "integer not null default 0")
    private int failedLoginAttempts = 0;

    /** Set once failedLoginAttempts crosses the threshold; login is refused until this passes. */
    private Instant lockedUntil;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Id of the account that provisioned this one (maintenance-created staff/maintenance). Null for self-registered clients. */
    @Column
    private UUID createdBy;

    protected User() {
    }

    public User(String fullName, String email, String passwordHash, Role role, UUID createdBy) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public void setSuperAdmin(boolean superAdmin) {
        this.superAdmin = superAdmin;
        if (!superAdmin) {
            this.activeRole = null;
        }
    }

    public Role getActiveRole() {
        return activeRole;
    }

    public void setActiveRole(Role activeRole) {
        this.activeRole = activeRole;
    }

    /** The role this account is currently operating as — a super admin's activeRole if set, else their real role. */
    public Role effectiveRole() {
        return superAdmin && activeRole != null ? activeRole : role;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /** Returns true if this failure just tripped the lock (5 consecutive failures). */
    public boolean registerFailedLogin() {
        failedLoginAttempts++;
        if (failedLoginAttempts >= 5) {
            lockedUntil = Instant.now().plusSeconds(15 * 60);
            failedLoginAttempts = 0;
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public void unlock() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }
}
