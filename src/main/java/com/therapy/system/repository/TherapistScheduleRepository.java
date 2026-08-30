package com.therapy.system.repository;

import com.therapy.system.model.TherapistSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.DayOfWeek;
import java.util.List;

public interface TherapistScheduleRepository extends JpaRepository<TherapistSchedule, Long> {
    List<TherapistSchedule> findByTherapistId(Long therapistId);
    List<TherapistSchedule> findByTherapistIdAndDayOfWeek(Long therapistId, DayOfWeek dayOfWeek);
}
