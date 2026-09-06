package com.mindcare.backend.auth;

import com.mindcare.backend.auth.dto.CreateStaffRequest;
import com.mindcare.backend.auth.dto.UserResponse;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Account provisioning reachable only by an authenticated MAINTENANCE user.
 * There is deliberately no public path to any of these three roles.
 */
@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasRole('MAINTENANCE')")
public class StaffController {

    private static final Set<Role> PROVISIONABLE_ROLES = Set.of(Role.THERAPIST, Role.RECEPTIONIST, Role.MAINTENANCE);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createStaff(@Valid @RequestBody CreateStaffRequest request, @AuthenticationPrincipal User currentUser) {
        if (!PROVISIONABLE_ROLES.contains(request.role())) {
            throw new IllegalArgumentException("Role must be one of " + PROVISIONABLE_ROLES);
        }

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User staff = new User(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                request.role(),
                currentUser.getId()
        );
        userRepository.save(staff);

        return UserResponse.from(staff);
    }

    @GetMapping
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }
}
