package com.mindcare.backend.auth.dto;

import com.mindcare.backend.model.User;

public record AuthResponse(
        String token,
        UserResponse user
) {
    public static AuthResponse of(String token, User user) {
        return new AuthResponse(token, UserResponse.from(user));
    }
}
