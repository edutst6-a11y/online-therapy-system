package com.mindcare.backend.auth;

import com.mindcare.backend.audit.AuditService;
import com.mindcare.backend.auth.dto.AuthResponse;
import com.mindcare.backend.auth.dto.LoginRequest;
import com.mindcare.backend.auth.dto.RegisterRequest;
import com.mindcare.backend.auth.dto.UserResponse;
import com.mindcare.backend.model.AuditResult;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.UserRepository;
import com.mindcare.backend.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    /**
     * Public self-registration. Always creates a CLIENT — the request body has no
     * role field, so there is nothing for a caller to spoof here.
     */
    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.CLIENT,
                null
        );
        userRepository.save(user);

        String token = jwtService.issueToken(user.getId());
        return AuthResponse.of(token, user);
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase();
        String ip = clientIp(httpRequest);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.record(null, email, "LOGIN", "User", null, ip, AuditResult.FAILURE, "Invalid credentials");
            throw new InvalidCredentialsException();
        }

        auditService.record(user, email, "LOGIN", "User", user.getId().toString(), ip, AuditResult.SUCCESS, null);
        String token = jwtService.issueToken(user.getId());
        return AuthResponse.of(token, user);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Returns the caller's identity as it stands in the database right now. */
    @GetMapping("/api/auth/me")
    public UserResponse me(@AuthenticationPrincipal User currentUser) {
        return UserResponse.from(currentUser);
    }
}
