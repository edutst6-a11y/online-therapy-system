package com.mindcare.backend.bootstrap;

import com.mindcare.backend.auth.EmailAlreadyRegisteredException;
import com.mindcare.backend.auth.dto.AuthResponse;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.UserRepository;
import com.mindcare.backend.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creates the very first MAINTENANCE account when the database has none.
 * Deliberately requires no auth (there's nobody to authenticate as yet), but
 * is only ever usable once — the moment a MAINTENANCE account exists, this
 * permanently returns 409 and does nothing. This exists so the first admin
 * can be provisioned without needing shell/dashboard access to the deployed
 * environment.
 */
@RestController
public class BootstrapController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public BootstrapController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public record BootstrapRequest(
            @NotBlank String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password
    ) {
    }

    @PostMapping("/api/bootstrap/maintenance")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse bootstrap(@Valid @RequestBody BootstrapRequest request) {
        if (userRepository.countByRole(Role.MAINTENANCE) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already bootstrapped");
        }

        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.MAINTENANCE,
                null
        );
        userRepository.save(user);

        String token = jwtService.issueToken(user.getId());
        return AuthResponse.of(token, user);
    }
}
