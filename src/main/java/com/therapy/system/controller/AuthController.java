package com.therapy.system.controller;

import com.therapy.system.model.Role;
import com.therapy.system.model.User;
import com.therapy.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        User user = new User();
        user.setUsername(request.get("username"));
        user.setPassword(request.get("password"));
        user.setEmail(request.get("email"));
        user.setName(request.get("name"));
        user.setSpecialization(request.get("specialization"));

        String roleStr = request.getOrDefault("role", "CLIENT").toUpperCase();
        try {
            user.setRole(Role.valueOf(roleStr));
        } catch (Exception e) {
            user.setRole(Role.CLIENT);
        }

        userRepository.save(user);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/therapists")
    public ResponseEntity<List<User>> getTherapists() {
        return ResponseEntity.ok(userRepository.findByRole(Role.THERAPIST));
    }
}
