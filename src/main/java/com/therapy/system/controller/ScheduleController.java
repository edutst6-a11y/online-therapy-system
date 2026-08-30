package com.therapy.system.controller;

import com.therapy.system.model.TherapistSchedule;
import com.therapy.system.repository.TherapistScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
@CrossOrigin(origins = "*")
public class ScheduleController {

    @Autowired
    private TherapistScheduleRepository scheduleRepository;

    @PostMapping
    public ResponseEntity<TherapistSchedule> setAvailability(@RequestBody TherapistSchedule schedule) {
        TherapistSchedule saved = scheduleRepository.save(schedule);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<TherapistSchedule>> getAllSchedules() {
        return ResponseEntity.ok(scheduleRepository.findAll());
    }

    @GetMapping("/therapist/{therapistId}")
    public ResponseEntity<List<TherapistSchedule>> getTherapistSchedule(@PathVariable Long therapistId) {
        return ResponseEntity.ok(scheduleRepository.findByTherapistId(therapistId));
    }
}
