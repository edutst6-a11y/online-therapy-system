package com.mindcare.backend.clinicalnote;

import com.mindcare.backend.clinicalnote.dto.CreateNoteRequest;
import com.mindcare.backend.clinicalnote.dto.NoteContent;
import com.mindcare.backend.clinicalnote.dto.NoteResponse;
import com.mindcare.backend.model.Appointment;
import com.mindcare.backend.model.ClinicalNote;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.AppointmentRepository;
import com.mindcare.backend.repository.ClinicalNoteRepository;
import com.mindcare.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Never reachable by CLIENT or RECEPTIONIST — clinical notes are the most
 * access-sensitive resource in the system per the blueprint. Draft notes
 * are freely editable by their author; signed notes are permanently
 * immutable and can only be corrected via {@link #amend}.
 */
@RestController
@RequestMapping("/api/clinical-notes")
@PreAuthorize("hasAnyRole('THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
@Transactional
public class ClinicalNoteController {

    private final ClinicalNoteRepository noteRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    public ClinicalNoteController(
            ClinicalNoteRepository noteRepository,
            UserRepository userRepository,
            AppointmentRepository appointmentRepository
    ) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('THERAPIST')")
    public NoteResponse create(@Valid @RequestBody CreateNoteRequest request, @AuthenticationPrincipal User therapist) {
        User client = userRepository.findById(request.clientId())
                .orElseThrow(() -> new NoSuchElementException("Client not found"));
        Appointment appointment = request.appointmentId() != null
                ? appointmentRepository.findById(request.appointmentId()).orElse(null)
                : null;

        ClinicalNote note = new ClinicalNote(client, therapist, appointment, null);
        applyContent(note, request.content());
        noteRepository.save(note);
        return NoteResponse.from(note);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('THERAPIST')")
    public NoteResponse update(@PathVariable UUID id, @Valid @RequestBody NoteContent content, @AuthenticationPrincipal User therapist) {
        ClinicalNote note = ownNoteOrThrow(id, therapist);
        if (note.isSigned()) {
            throw new IllegalArgumentException("This note is signed and can no longer be edited — use amend instead");
        }
        applyContent(note, content);
        note.touch();
        noteRepository.save(note);
        return NoteResponse.from(note);
    }

    @PatchMapping("/{id}/sign")
    @PreAuthorize("hasRole('THERAPIST')")
    public NoteResponse sign(@PathVariable UUID id, @AuthenticationPrincipal User therapist) {
        ClinicalNote note = ownNoteOrThrow(id, therapist);
        if (note.isSigned()) {
            throw new IllegalArgumentException("This note is already signed");
        }
        note.sign();
        noteRepository.save(note);
        return NoteResponse.from(note);
    }

    /** Corrects a signed note by creating a brand new draft that references it — the original is untouched. */
    @PostMapping("/{id}/amend")
    @PreAuthorize("hasRole('THERAPIST')")
    public NoteResponse amend(@PathVariable UUID id, @AuthenticationPrincipal User therapist) {
        ClinicalNote original = ownNoteOrThrow(id, therapist);
        if (!original.isSigned()) {
            throw new IllegalArgumentException("Only signed notes can be amended");
        }

        ClinicalNote amendment = new ClinicalNote(original.getClient(), therapist, original.getAppointment(), original.getId());
        applyContent(amendment, new NoteContent(
                original.getSessionType(), original.getPresentingConcerns(), original.getClinicalObservations(),
                original.getInterventions(), original.getClientResponse(), original.getRiskAssessment(),
                original.getPlan(), original.getFollowUp()
        ));
        noteRepository.save(amendment);
        return NoteResponse.from(amendment);
    }

    @GetMapping("/client/{clientId}")
    public List<NoteResponse> forClient(@PathVariable UUID clientId) {
        return noteRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(NoteResponse::from).toList();
    }

    @GetMapping("/{id}")
    public NoteResponse get(@PathVariable UUID id) {
        return NoteResponse.from(findOrThrow(id));
    }

    private void applyContent(ClinicalNote note, NoteContent content) {
        note.setSessionType(content.sessionType());
        note.setPresentingConcerns(content.presentingConcerns());
        note.setClinicalObservations(content.clinicalObservations());
        note.setInterventions(content.interventions());
        note.setClientResponse(content.clientResponse());
        note.setRiskAssessment(content.riskAssessment());
        note.setPlan(content.plan());
        note.setFollowUp(content.followUp());
    }

    private ClinicalNote findOrThrow(UUID id) {
        return noteRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Note not found"));
    }

    private ClinicalNote ownNoteOrThrow(UUID id, User therapist) {
        ClinicalNote note = findOrThrow(id);
        if (!note.getTherapist().getId().equals(therapist.getId())) {
            throw new AccessDeniedException("Not your note");
        }
        return note;
    }
}
