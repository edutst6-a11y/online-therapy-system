package com.mindcare.backend.clinicalnote.dto;

import com.mindcare.backend.model.ClinicalNote;
import com.mindcare.backend.model.NoteStatus;

import java.time.Instant;
import java.util.UUID;

public record NoteResponse(
        UUID id,
        UUID clientId,
        String clientName,
        UUID therapistId,
        String therapistName,
        UUID appointmentId,
        UUID amendsNoteId,
        NoteStatus status,
        String sessionType,
        String presentingConcerns,
        String clinicalObservations,
        String interventions,
        String clientResponse,
        String riskAssessment,
        String plan,
        String followUp,
        Instant createdAt,
        Instant updatedAt,
        Instant signedAt
) {
    public static NoteResponse from(ClinicalNote n) {
        return new NoteResponse(
                n.getId(),
                n.getClient().getId(),
                n.getClient().getFullName(),
                n.getTherapist().getId(),
                n.getTherapist().getFullName(),
                n.getAppointment() != null ? n.getAppointment().getId() : null,
                n.getAmendsNoteId(),
                n.getStatus(),
                n.getSessionType(),
                n.getPresentingConcerns(),
                n.getClinicalObservations(),
                n.getInterventions(),
                n.getClientResponse(),
                n.getRiskAssessment(),
                n.getPlan(),
                n.getFollowUp(),
                n.getCreatedAt(),
                n.getUpdatedAt(),
                n.getSignedAt()
        );
    }
}
