package com.mindcare.backend.auth.dto;

import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;

import java.time.Instant;
import java.util.UUID;

/**
 * role is the effective role the account is currently operating as — for a super
 * admin who has switched their active role, that differs from trueRole, which is
 * always their real underlying account role and never changes.
 */
public record UserResponse(
        UUID id,
        String fullName,
        String email,
        Role role,
        Role trueRole,
        boolean superAdmin,
        boolean enabled,
        boolean locked,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getFullName(), user.getEmail(),
                user.effectiveRole(), user.getRole(), user.isSuperAdmin(),
                user.isEnabled(), user.isLocked(), user.getCreatedAt()
        );
    }
}
