package com.mindcare.backend.clinicalnote.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateNoteRequest(
        @NotNull UUID clientId,
        UUID appointmentId,
        @Valid @NotNull NoteContent content
) {
}
