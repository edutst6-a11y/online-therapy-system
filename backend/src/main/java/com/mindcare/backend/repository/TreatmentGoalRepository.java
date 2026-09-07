package com.mindcare.backend.repository;

import com.mindcare.backend.model.TreatmentGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TreatmentGoalRepository extends JpaRepository<TreatmentGoal, UUID> {
}
