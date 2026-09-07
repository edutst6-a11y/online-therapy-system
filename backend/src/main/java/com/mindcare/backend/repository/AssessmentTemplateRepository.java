package com.mindcare.backend.repository;

import com.mindcare.backend.model.AssessmentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssessmentTemplateRepository extends JpaRepository<AssessmentTemplate, UUID> {

    List<AssessmentTemplate> findByActiveTrue();
}
