package com.mindcare.backend.therapist;

import com.mindcare.backend.auth.dto.UserResponse;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.repository.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lets any authenticated user browse therapists to book a session with. */
@RestController
@RequestMapping("/api/therapists")
public class TherapistController {

    private final UserRepository userRepository;

    public TherapistController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.THERAPIST)
                .map(UserResponse::from)
                .toList();
    }
}
