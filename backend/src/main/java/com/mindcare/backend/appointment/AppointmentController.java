package com.mindcare.backend.appointment;

import com.mindcare.backend.appointment.dto.AppointmentResponse;
import com.mindcare.backend.appointment.dto.BookAppointmentRequest;
import com.mindcare.backend.appointment.dto.RescheduleRequest;
import com.mindcare.backend.appointment.dto.SetMeetLinkRequest;
import com.mindcare.backend.model.Appointment;
import com.mindcare.backend.model.AppointmentStatus;
import com.mindcare.backend.model.AvailabilitySlot;
import com.mindcare.backend.model.NotificationType;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;
import com.mindcare.backend.notification.NotificationService;
import com.mindcare.backend.repository.AppointmentRepository;
import com.mindcare.backend.repository.AvailabilitySlotRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
@Transactional
public class AppointmentController {

    private final AppointmentRepository appointmentRepository;
    private final AvailabilitySlotRepository availabilityRepository;
    private final NotificationService notificationService;

    public AppointmentController(
            AppointmentRepository appointmentRepository,
            AvailabilitySlotRepository availabilityRepository,
            NotificationService notificationService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.availabilityRepository = availabilityRepository;
        this.notificationService = notificationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CLIENT')")
    public AppointmentResponse book(@Valid @RequestBody BookAppointmentRequest request, @AuthenticationPrincipal User client) {
        AvailabilitySlot slot = availabilityRepository.findById(request.availabilitySlotId())
                .orElseThrow(() -> new NoSuchElementException("Slot not found"));

        if (slot.isBooked()) {
            throw new IllegalArgumentException("That slot is no longer available");
        }

        slot.setBooked(true);
        availabilityRepository.save(slot);

        int durationMinutes = (int) Duration.between(slot.getStartTime(), slot.getEndTime()).toMinutes();
        Appointment appointment = new Appointment(client, slot.getTherapist(), slot.getStartTime(), durationMinutes, request.notes(), slot);
        appointmentRepository.save(appointment);

        notificationService.notify(slot.getTherapist(), NotificationType.APPOINTMENT_REQUESTED,
                "New session request", client.getFullName() + " requested a session with you.", appointment.getId());

        return AppointmentResponse.from(appointment);
    }

    /** The caller's own appointments — as a client, or as the assigned therapist. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('CLIENT', 'THERAPIST')")
    public List<AppointmentResponse> mine(@AuthenticationPrincipal User user) {
        List<Appointment> appointments = user.getRole() == Role.CLIENT
                ? appointmentRepository.findByClientIdOrderByScheduledAtDesc(user.getId())
                : appointmentRepository.findByTherapistIdOrderByScheduledAtDesc(user.getId());
        return appointments.stream().map(AppointmentResponse::from).toList();
    }

    /** Every appointment — for front-desk triage and clinic-wide oversight. */
    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MAINTENANCE')")
    public List<AppointmentResponse> all() {
        return appointmentRepository.findAll().stream().map(AppointmentResponse::from).toList();
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MAINTENANCE')")
    public AppointmentResponse approve(@PathVariable UUID id) {
        Appointment appointment = findOrThrow(id);
        appointment.setStatus(AppointmentStatus.APPROVED);
        appointmentRepository.save(appointment);
        notificationService.notify(appointment.getClient(), NotificationType.APPOINTMENT_APPROVED,
                "Session approved", "Your session with " + appointment.getTherapist().getFullName() + " was approved.", appointment.getId());
        return AppointmentResponse.from(appointment);
    }

    /**
     * The therapist starts a normal Google Meet call themselves (meet.google.com/new,
     * under their own Google account) and pastes the resulting link here so the
     * client can join the same call. No backend Google integration involved.
     */
    @PatchMapping("/{id}/meet-link")
    @PreAuthorize("hasAnyRole('THERAPIST', 'MAINTENANCE')")
    public AppointmentResponse setMeetLink(@PathVariable UUID id, @Valid @RequestBody SetMeetLinkRequest request, @AuthenticationPrincipal User user) {
        Appointment appointment = findOrThrow(id);
        if (user.getRole() == Role.THERAPIST && !appointment.getTherapist().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your appointment");
        }
        appointment.setMeetLink(request.meetLink());
        appointmentRepository.save(appointment);
        return AppointmentResponse.from(appointment);
    }

    @PatchMapping("/{id}/decline")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MAINTENANCE')")
    public AppointmentResponse decline(@PathVariable UUID id) {
        Appointment appointment = findOrThrow(id);
        freeSlot(appointment);
        appointment.setStatus(AppointmentStatus.DECLINED);
        appointmentRepository.save(appointment);
        notificationService.notify(appointment.getClient(), NotificationType.APPOINTMENT_DECLINED,
                "Session declined", "Your requested session with " + appointment.getTherapist().getFullName() + " was declined.", appointment.getId());
        return AppointmentResponse.from(appointment);
    }

    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MAINTENANCE')")
    public AppointmentResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
        Appointment appointment = findOrThrow(id);
        appointment.setScheduledAt(request.newScheduledAt());
        appointment.setMeetLink(null);
        appointment.setStatus(AppointmentStatus.RESCHEDULED);
        appointmentRepository.save(appointment);
        notificationService.notify(appointment.getClient(), NotificationType.APPOINTMENT_RESCHEDULED,
                "Session rescheduled", "Your session with " + appointment.getTherapist().getFullName() + " was rescheduled.", appointment.getId());
        return AppointmentResponse.from(appointment);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('CLIENT', 'RECEPTIONIST', 'MAINTENANCE')")
    public AppointmentResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        Appointment appointment = findOrThrow(id);
        if (user.getRole() == Role.CLIENT && !appointment.getClient().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your appointment");
        }
        freeSlot(appointment);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
        if (user.getRole() == Role.CLIENT) {
            notificationService.notify(appointment.getTherapist(), NotificationType.APPOINTMENT_CANCELLED,
                    "Session cancelled", appointment.getClient().getFullName() + " cancelled their session.", appointment.getId());
        } else {
            notificationService.notify(appointment.getClient(), NotificationType.APPOINTMENT_CANCELLED,
                    "Session cancelled", "Your session with " + appointment.getTherapist().getFullName() + " was cancelled.", appointment.getId());
            notificationService.notify(appointment.getTherapist(), NotificationType.APPOINTMENT_CANCELLED,
                    "Session cancelled", "Your session with " + appointment.getClient().getFullName() + " was cancelled.", appointment.getId());
        }
        return AppointmentResponse.from(appointment);
    }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('THERAPIST', 'MAINTENANCE')")
    public AppointmentResponse complete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        Appointment appointment = findOrThrow(id);
        if (user.getRole() == Role.THERAPIST && !appointment.getTherapist().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your appointment");
        }
        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);
        return AppointmentResponse.from(appointment);
    }

    private Appointment findOrThrow(UUID id) {
        return appointmentRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Appointment not found"));
    }

    private void freeSlot(Appointment appointment) {
        AvailabilitySlot slot = appointment.getAvailabilitySlot();
        if (slot != null) {
            slot.setBooked(false);
            availabilityRepository.save(slot);
        }
    }
}
