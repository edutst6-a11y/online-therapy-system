package com.therapy.system.controller;

import com.therapy.system.model.Role;
import com.therapy.system.model.User;
import com.therapy.system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<User>> getUsersByRole(@PathVariable String role) {
        try {
            Role enumRole = Role.valueOf(role.toUpperCase());
            return ResponseEntity.ok(userRepository.findByRole(enumRole));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
