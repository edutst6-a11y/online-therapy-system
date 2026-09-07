package com.mindcare.backend.auth.dto;

import com.mindcare.backend.model.Role;

/** role null means: revert to my real underlying role. */
public record SetActiveRoleRequest(Role role) {
}
