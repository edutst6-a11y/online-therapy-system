package com.mindcare.backend.repository;

import com.mindcare.backend.model.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

    List<AvailabilitySlot> findByTherapistIdAndBookedFalseOrderByStartTimeAsc(UUID therapistId);
}
