package com.mindcare.backend.repository;

import com.mindcare.backend.model.AssessmentResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssessmentResponseRepository extends JpaRepository<AssessmentResponse, UUID> {

    List<AssessmentResponse> findByClientIdOrderByAssignedAtDesc(UUID clientId);
}
