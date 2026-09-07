package com.mindcare.backend.audit;

import com.mindcare.backend.model.AuditResult;
import com.mindcare.backend.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Blanket coverage for every state-changing request (POST/PUT/PATCH/DELETE):
 * logged generically here so no controller has to remember to call AuditService
 * itself. Login/register are excluded and handled explicitly in AuthController
 * instead, since they need the attempted email even when there's no authenticated
 * actor yet — something this filter can't see without parsing the request body.
 */
public class AuditLoggingFilter extends OncePerRequestFilter {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final List<String> EXCLUDED_PATHS = List.of("/api/auth/login", "/api/auth/register");

    private final AuditService auditService;

    public AuditLoggingFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        boolean auditable = !method.equals("GET") && !method.equals("OPTIONS")
                && path.startsWith("/api/") && !EXCLUDED_PATHS.contains(path);

        filterChain.doFilter(request, response);

        if (!auditable) {
            return;
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        User actor = auth != null && auth.getPrincipal() instanceof User u ? u : null;

        String[] segments = path.split("/");
        String recordType = segments.length > 2 ? segments[2] : null;
        String recordId = null;
        for (String segment : segments) {
            if (UUID_PATTERN.matcher(segment).matches()) {
                recordId = segment;
            }
        }

        int status = response.getStatus();
        AuditResult result = status < 400 ? AuditResult.SUCCESS : AuditResult.FAILURE;

        auditService.record(actor, "Unauthenticated", method + " " + path, recordType, recordId,
                clientIp(request), result, "HTTP " + status);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
