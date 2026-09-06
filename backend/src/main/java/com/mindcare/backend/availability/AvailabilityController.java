package com.mindcare.backend.availability;

import com.mindcare.backend.availability.dto.AvailabilitySlotResponse;
import com.mindcare.backend.availability.dto.CreateAvailabilityRequest;
import com.mindcare.backend.model.AvailabilitySlot;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.AvailabilitySlotRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/availability")
@Transactional
public class AvailabilityController {

    private final AvailabilitySlotRepository availabilityRepository;

    public AvailabilityController(AvailabilitySlotRepository availabilityRepository) {
        this.availabilityRepository = availabilityRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('THERAPIST')")
    public AvailabilitySlotResponse publish(@Valid @RequestBody CreateAvailabilityRequest request, @AuthenticationPrincipal User therapist) {
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        AvailabilitySlot slot = new AvailabilitySlot(therapist, request.startTime(), request.endTime());
        availabilityRepository.save(slot);
        return AvailabilitySlotResponse.from(slot);
    }

    /** The therapist's own slots, including already-booked ones. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('THERAPIST')")
    public List<AvailabilitySlotResponse> mine(@AuthenticationPrincipal User therapist) {
        return availabilityRepository.findByTherapistIdOrderByStartTimeAsc(therapist.getId()).stream()
                .map(AvailabilitySlotResponse::from)
                .toList();
    }

    /** Open slots for a given therapist — used by clients to pick a time when booking. */
    @GetMapping("/therapist/{therapistId}")
    public List<AvailabilitySlotResponse> openSlotsFor(@PathVariable UUID therapistId) {
        return availabilityRepository.findByTherapistIdAndBookedFalseOrderByStartTimeAsc(therapistId).stream()
                .map(AvailabilitySlotResponse::from)
                .toList();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('THERAPIST')")
    public void remove(@PathVariable UUID id, @AuthenticationPrincipal User therapist) {
        AvailabilitySlot slot = availabilityRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Slot not found"));

        if (!slot.getTherapist().getId().equals(therapist.getId())) {
            throw new AccessDeniedException("Not your slot");
        }
        if (slot.isBooked()) {
            throw new IllegalArgumentException("Can't remove a slot that's already booked");
        }
        availabilityRepository.delete(slot);
    }
}
