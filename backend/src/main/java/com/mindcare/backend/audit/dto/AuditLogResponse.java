package com.mindcare.backend.audit.dto;

import com.mindcare.backend.model.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String actorName,
        String action,
        String recordType,
        String recordId,
        String ipAddress,
        String result,
        String detail,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getActorName(), log.getAction(), log.getRecordType(), log.getRecordId(),
                log.getIpAddress(), log.getResult().name(), log.getDetail(), log.getCreatedAt()
        );
    }
}
