package com.therapy.system.controller;

import com.therapy.system.model.Appointment;
import com.therapy.system.repository.AppointmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/appointments")
@CrossOrigin(origins = "*")
public class AppointmentController {

    private final AppointmentRepository appointmentRepository;

    public AppointmentController(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @PostMapping
    public ResponseEntity<?> createAppointment(@RequestBody Appointment appointment) {
        try {
            // Validate required fields
            if (appointment.getTherapistId() == null || appointment.getClientId() == null || appointment.getAppointmentDateTime() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Missing required fields: clientId, therapistId, and appointmentDateTime are mandatory."));
            }

            // Check if therapist already has an active booking at the specified slot
            boolean isBooked = appointmentRepository
                    .existsByTherapistIdAndAppointmentDateTimeAndStatusNot(
                            appointment.getTherapistId(),
                            appointment.getAppointmentDateTime(),
                            "CANCELLED"
                    );

            if (isBooked) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Therapist is already booked for this selected time slot."));
            }

            if (appointment.getStatus() == null || appointment.getStatus().isBlank()) {
                appointment.setStatus("PENDING");
            }

            Appointment saved = appointmentRepository.save(appointment);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process appointment booking: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Appointment>> getAllAppointments() {
        return ResponseEntity.ok(appointmentRepository.findAll());
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<Appointment>> getClientAppointments(@PathVariable Long clientId) {
        List<Appointment> clientAppointments = appointmentRepository.findAll().stream()
                .filter(a -> a.getClientId() != null && a.getClientId().equals(clientId))
                .toList();
        return ResponseEntity.ok(clientAppointments);
    }

    @GetMapping("/therapist/{therapistId}")
    public ResponseEntity<List<Appointment>> getTherapistAppointments(@PathVariable Long therapistId) {
        List<Appointment> therapistAppointments = appointmentRepository.findAll().stream()
                .filter(a -> a.getTherapistId() != null && a.getTherapistId().equals(therapistId))
                .toList();
        return ResponseEntity.ok(therapistAppointments);
    }

  @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, String> request) {

        try {
            return appointmentRepository.findById(id).map(app -> {
                if (request.containsKey("status") && request.get("status") != null) {
                    app.setStatus(request.get("status").toUpperCase());
                }
                if (request.containsKey("receptionistId") && request.get("receptionistId") != null) {
                    try {
                        app.setReceptionistId(Long.parseLong(request.get("receptionistId")));
                    } catch (NumberFormatException ignored) {}
                }
                Appointment saved = appointmentRepository.save(app);
                return ResponseEntity.ok((Object) saved);
            }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body((Object) Map.of("error", "Appointment not found with ID: " + id)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Failed to update appointment status: " + e.getMessage()));
        }
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<?> rescheduleAppointment(
            @PathVariable Long id, 
            @RequestBody Map<String, String> request) {

        try {
            if (!request.containsKey("newDateTime") || request.get("newDateTime") == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Missing required field: newDateTime"));
            }

            LocalDateTime newDateTime;
            try {
                newDateTime = LocalDateTime.parse(request.get("newDateTime"));
            } catch (DateTimeParseException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Invalid date format. Use ISO format (YYYY-MM-DDTHH:mm)."));
            }

            String reason = request.getOrDefault("reason", "Rescheduled by clinic staff");

            return appointmentRepository.findById(id).map(app -> {
                boolean isBooked = appointmentRepository
                        .existsByTherapistIdAndAppointmentDateTimeAndStatusNot(
                                app.getTherapistId(), newDateTime, "CANCELLED");

                if (isBooked) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Target time slot is unavailable."));
                }

                app.setAppointmentDateTime(newDateTime);
                app.setStatus("RESCHEDULED");
                app.setRescheduleReason(reason);
                Appointment saved = appointmentRepository.save(app);
                return ResponseEntity.ok(saved);
            }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Appointment not found with ID: " + id)));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reschedule appointment: " + e.getMessage()));
        }
    }
}
