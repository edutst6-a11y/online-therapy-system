package com.mindcare.backend.document.dto;

import com.mindcare.backend.model.Document;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummary(
        UUID id,
        UUID clientId,
        String clientName,
        UUID uploadedById,
        String uploadedByName,
        String fileName,
        String contentType,
        long fileSize,
        String description,
        Instant createdAt
) {
    public static DocumentSummary from(Document d) {
        return new DocumentSummary(
                d.getId(), d.getClient().getId(), d.getClient().getFullName(),
                d.getUploadedBy().getId(), d.getUploadedBy().getFullName(),
                d.getFileName(), d.getContentType(), d.getFileSize(), d.getDescription(), d.getCreatedAt()
        );
    }
}
