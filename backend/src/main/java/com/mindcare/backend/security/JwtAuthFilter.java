package com.mindcare.backend.security;

import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authenticates each request from its Bearer token, then loads the user's CURRENT
 * role from the database rather than trusting anything encoded in the token itself.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Optional<UUID> userId = jwtService.extractUserId(token);

            if (userId.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
                userRepository.findById(userId.get())
                        .filter(User::isEnabled)
                        .ifPresent(user -> {
                            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.effectiveRole().name()));
                            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            }
        }

        filterChain.doFilter(request, response);
    }
}
