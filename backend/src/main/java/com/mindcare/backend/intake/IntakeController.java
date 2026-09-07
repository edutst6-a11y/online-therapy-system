package com.mindcare.backend.intake;

import com.mindcare.backend.intake.dto.IntakeResponse;
import com.mindcare.backend.intake.dto.SubmitIntakeRequest;
import com.mindcare.backend.model.ClientIntake;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.ClientIntakeRepository;
import com.mindcare.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Client onboarding intake — Phase 2 item 10 of the blueprint. Deliberately
 * not exposed to Receptionist: reasonForSeekingCare is clinical context,
 * and the blueprint is explicit that reception gets operational info only.
 */
@RestController
@RequestMapping("/api/intake")
@Transactional
public class IntakeController {

    private final ClientIntakeRepository intakeRepository;
    private final UserRepository userRepository;

    public IntakeController(ClientIntakeRepository intakeRepository, UserRepository userRepository) {
        this.intakeRepository = intakeRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    public IntakeResponse submit(@Valid @RequestBody SubmitIntakeRequest request, @AuthenticationPrincipal User client) {
        ClientIntake intake = intakeRepository.findByClientId(client.getId())
                .orElseGet(() -> new ClientIntake(client));

        intake.setDateOfBirth(request.dateOfBirth());
        intake.setPhone(request.phone());
        intake.setEmergencyContactName(request.emergencyContactName());
        intake.setEmergencyContactPhone(request.emergencyContactPhone());
        intake.setReasonForSeekingCare(request.reasonForSeekingCare());
        intake.setConsentGiven(request.consentGiven());
        intake.touch();

        intakeRepository.save(intake);
        return IntakeResponse.from(intake);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public IntakeResponse mine(@AuthenticationPrincipal User client) {
        return intakeRepository.findByClientId(client.getId())
                .map(IntakeResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No intake submitted yet"));
    }

    /** Clinical/admin view of a specific client's intake — not for Reception. */
    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
    public IntakeResponse forClient(@PathVariable UUID clientId) {
        userRepository.findById(clientId).orElseThrow(() -> new NoSuchElementException("Client not found"));
        return intakeRepository.findByClientId(clientId)
                .map(IntakeResponse::from)
                .orElseThrow(() -> new NoSuchElementException("This client hasn't submitted an intake yet"));
    }
}
