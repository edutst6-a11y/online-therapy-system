package com.mindcare.backend.repository;

import com.mindcare.backend.model.TreatmentPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, UUID> {

    List<TreatmentPlan> findByClientIdOrderByCreatedAtDesc(UUID clientId);
}
