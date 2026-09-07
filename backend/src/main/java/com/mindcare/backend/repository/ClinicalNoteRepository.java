package com.mindcare.backend.repository;

import com.mindcare.backend.model.ClinicalNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClinicalNoteRepository extends JpaRepository<ClinicalNote, UUID> {

    List<ClinicalNote> findByClientIdOrderByCreatedAtDesc(UUID clientId);
}
