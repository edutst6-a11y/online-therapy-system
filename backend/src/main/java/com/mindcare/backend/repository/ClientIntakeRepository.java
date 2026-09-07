package com.mindcare.backend.repository;

import com.mindcare.backend.model.ClientIntake;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientIntakeRepository extends JpaRepository<ClientIntake, UUID> {

    Optional<ClientIntake> findByClientId(UUID clientId);
}
