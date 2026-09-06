package com.mindcare.backend.repository;

import com.mindcare.backend.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByClientIdOrderByScheduledAtDesc(UUID clientId);

    List<Appointment> findByTherapistIdOrderByScheduledAtDesc(UUID therapistId);
}
