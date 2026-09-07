package com.mindcare.backend.document.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * clientId is required when staff upload on a client's behalf, and ignored when a
 * client uploads their own document — the server always attributes the upload to
 * whoever is authenticated, never to a client-supplied id.
 */
public record UploadDocumentRequest(
        UUID clientId,
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotBlank String base64Content,
        String description
) {
}
