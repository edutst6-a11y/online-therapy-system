package com.mindcare.backend.audit;

import com.mindcare.backend.audit.dto.AuditLogResponse;
import com.mindcare.backend.repository.AuditLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only, Maintenance-only. There is deliberately no update or delete
 * endpoint anywhere for this resource — the audit trail is append-only.
 */
@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('MAINTENANCE')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public List<AuditLogResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        int cappedSize = Math.min(size, 200);
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, cappedSize)).stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
