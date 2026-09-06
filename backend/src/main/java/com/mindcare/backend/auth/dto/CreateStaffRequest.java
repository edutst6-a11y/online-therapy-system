package com.mindcare.backend.auth.dto;

import com.mindcare.backend.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Used only by an authenticated MAINTENANCE account to provision Therapist,
 * Receptionist, or additional Maintenance accounts. Clients can never reach
 * this path — there is no self-service way to become staff.
 */
public record CreateStaffRequest(
        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotNull(message = "Role is required")
        Role role
) {
}
