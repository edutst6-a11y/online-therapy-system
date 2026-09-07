package com.mindcare.backend.document;

import com.mindcare.backend.document.dto.DocumentSummary;
import com.mindcare.backend.document.dto.UploadDocumentRequest;
import com.mindcare.backend.model.Document;
import com.mindcare.backend.model.NotificationType;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;
import com.mindcare.backend.notification.NotificationService;
import com.mindcare.backend.repository.DocumentRepository;
import com.mindcare.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Client documents — consent forms, referral letters, ID scans. Not exposed to
 * Reception, matching the intake/clinical-notes precedent for clinical records.
 * Files are stored inline in Postgres rather than a separate blob store, which
 * is enough for the small clinic documents this handles; a 5MB cap keeps that
 * reasonable.
 */
@RestController
@RequestMapping("/api/documents")
@Transactional
public class DocumentController {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public DocumentController(
            DocumentRepository documentRepository,
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('CLIENT', 'THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
    public DocumentSummary upload(@Valid @RequestBody UploadDocumentRequest request, @AuthenticationPrincipal User uploader) {
        User client;
        if (uploader.getRole() == Role.CLIENT) {
            client = uploader;
        } else {
            if (request.clientId() == null) {
                throw new IllegalArgumentException("clientId is required");
            }
            client = userRepository.findById(request.clientId())
                    .orElseThrow(() -> new NoSuchElementException("Client not found"));
            if (client.getRole() != Role.CLIENT) {
                throw new IllegalArgumentException("Documents can only be attached to a client's record");
            }
        }

        byte[] content;
        try {
            content = Base64.getDecoder().decode(request.base64Content());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("base64Content is not valid base64");
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("File is empty");
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds the 5MB limit");
        }

        Document document = new Document(client, uploader, request.fileName(), request.contentType(), content, request.description());
        documentRepository.save(document);

        if (!uploader.getId().equals(client.getId())) {
            notificationService.notify(client, NotificationType.DOCUMENT_UPLOADED,
                    "New document", uploader.getFullName() + " added \"" + request.fileName() + "\" to your record.", document.getId());
        }

        return DocumentSummary.from(document);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public List<DocumentSummary> mine(@AuthenticationPrincipal User client) {
        return documentRepository.findByClientIdOrderByCreatedAtDesc(client.getId()).stream()
                .map(DocumentSummary::from).toList();
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
    public List<DocumentSummary> forClient(@PathVariable UUID clientId) {
        return documentRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(DocumentSummary::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        Document document = findOrThrow(id);
        if (user.getRole() == Role.CLIENT && !document.getClient().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your document");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(document.getFileName()).build().toString())
                .body(document.getContent());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        Document document = findOrThrow(id);
        boolean owner = document.getUploadedBy().getId().equals(user.getId());
        if (!owner && user.getRole() != Role.MAINTENANCE) {
            throw new AccessDeniedException("Only the uploader or Maintenance can remove this document");
        }
        documentRepository.delete(document);
    }

    private Document findOrThrow(UUID id) {
        return documentRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Document not found"));
    }
}
