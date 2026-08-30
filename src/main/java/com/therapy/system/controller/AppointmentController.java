package com.therapy.system.controller;

import com.therapy.system.model.Appointment;
import com.therapy.system.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/appointments")
@CrossOrigin(origins = "*")
public class AppointmentController {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @PostMapping
    public ResponseEntity<?> createAppointment(@RequestBody Appointment appointment) {
        boolean isBooked = appointmentRepository
            .existsByTherapistIdAndAppointmentDateTimeAndStatusNot(
                appointment.getTherapistId(),
                appointment.getAppointmentDateTime(),
                "CANCELLED"
            );

        if (isBooked) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Therapist is already booked for this selected time slot.");
        }

        appointment.setStatus("PENDING");
        Appointment saved = appointmentRepository.save(appointment);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @GetMapping("/client/{clientId}")
    public List<Appointment> getClientAppointments(@PathVariable Long clientId) {
        return appointmentRepository.findAll().stream()
                .filter(a -> a.getClientId().equals(clientId))
                .toList();
    }

    @GetMapping("/therapist/{therapistId}")
    public List<Appointment> getTherapistAppointments(@PathVariable Long therapistId) {
        return appointmentRepository.findAll().stream()
                .filter(a -> a.getTherapistId().equals(therapistId))
                .toList();
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, String> request) {
        
        return appointmentRepository.findById(id).map(app -> {
            if (request.containsKey("status")) {
                app.setStatus(request.get("status").toUpperCase());
            }
            if (request.containsKey("receptionistId")) {
                app.setReceptionistId(Long.parseLong(request.get("receptionistId")));
            }
            appointmentRepository.save(app);
            return ResponseEntity.ok(app);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<?> rescheduleAppointment(
            @PathVariable Long id, 
            @RequestBody Map<String, String> request) {

        return appointmentRepository.findById(id).map(app -> {
            LocalDateTime newDateTime = LocalDateTime.parse(request.get("newDateTime"));
            String reason = request.getOrDefault("reason", "Rescheduled by clinic staff");

            boolean isBooked = appointmentRepository
                .existsByTherapistIdAndAppointmentDateTimeAndStatusNot(
                    app.getTherapistId(), newDateTime, "CANCELLED");

            if (isBooked) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Target time slot is unavailable.");
            }

            app.setAppointmentDateTime(newDateTime);
            app.setStatus("RESCHEDULED");
            app.setRescheduleReason(reason);
            appointmentRepository.save(app);
            return ResponseEntity.ok(app);
        }).orElse(ResponseEntity.notFound().build());
    }
}
