package com.mindcare.backend.audit;

import com.mindcare.backend.model.AuditLog;
import com.mindcare.backend.model.AuditResult;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Never allowed to break the request it's auditing: a failure here is logged
     * and swallowed rather than propagated.
     */
    public void record(User actor, String actorNameFallback, String action, String recordType, String recordId,
                        String ipAddress, AuditResult result, String detail) {
        try {
            String actorName = actor != null ? actor.getFullName() + " (" + actor.getRole() + ")" : actorNameFallback;
            auditLogRepository.save(new AuditLog(actor, actorName, action, recordType, recordId, ipAddress, result, detail));
        } catch (Exception e) {
            log.warn("Failed to write audit log entry for action '{}': {}", action, e.getMessage());
        }
    }
}
