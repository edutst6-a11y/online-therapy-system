package com.therapy.system.repository;

import com.therapy.system.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    boolean existsByTherapistIdAndAppointmentDateTimeAndStatusNot(
        Long therapistId, 
        LocalDateTime appointmentDateTime, 
        String status
    );
}
