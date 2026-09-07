package com.mindcare.backend.auth;

import com.mindcare.backend.audit.AuditService;
import com.mindcare.backend.auth.dto.AuthResponse;
import com.mindcare.backend.auth.dto.LoginRequest;
import com.mindcare.backend.auth.dto.RegisterRequest;
import com.mindcare.backend.auth.dto.SetActiveRoleRequest;
import com.mindcare.backend.auth.dto.UserResponse;
import com.mindcare.backend.model.AuditResult;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.UserRepository;
import com.mindcare.backend.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    /**
     * Deliberately NOT @Transactional: the failed-attempt counter must be saved and
     * committed even on the branch that then throws — a wrapping transaction here
     * would roll that write back along with the exception, silently defeating the
     * lockout this method exists to enforce.
     */
    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String email = request.email().trim().toLowerCase();
        String ip = clientIp(httpRequest);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user != null && user.isLocked()) {
            auditService.record(user, email, "LOGIN", "User", user.getId().toString(), ip, AuditResult.FAILURE, "Account locked");
            throw new AccountLockedException("Too many failed attempts. Try again in a few minutes.");
        }

        if (user == null || !user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            String detail = "Invalid credentials";
            if (user != null) {
                boolean justLocked = user.registerFailedLogin();
                userRepository.save(user);
                if (justLocked) {
                    detail = "Account locked after repeated failures";
                }
            }
            auditService.record(user, email, "LOGIN", "User", user != null ? user.getId().toString() : null, ip, AuditResult.FAILURE, detail);
            throw new InvalidCredentialsException();
        }

        user.registerSuccessfulLogin();
        userRepository.save(user);
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

    /**
     * Only a super admin can call this — everyone else's role comes exclusively
     * from their real `role` column. Switching doesn't issue a new token; the JWT
     * only ever carries a user id, so the next request re-derives the (now
     * switched) effective role fresh from the database, same as always.
     */
    @PatchMapping("/api/auth/active-role")
    @Transactional
    public UserResponse setActiveRole(@RequestBody SetActiveRoleRequest request, @AuthenticationPrincipal User currentUser) {
        if (!currentUser.isSuperAdmin()) {
            throw new AccessDeniedException("Only a super admin can switch roles");
        }
        currentUser.setActiveRole(request.role());
        userRepository.save(currentUser);
        auditService.record(currentUser, currentUser.getEmail(), "SWITCH_ACTIVE_ROLE", "User", currentUser.getId().toString(),
                "-", AuditResult.SUCCESS, "Now acting as " + currentUser.effectiveRole());
        return UserResponse.from(currentUser);
    }
}
