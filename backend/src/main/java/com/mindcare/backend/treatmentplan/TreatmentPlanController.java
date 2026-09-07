package com.mindcare.backend.treatmentplan;

import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.TreatmentGoal;
import com.mindcare.backend.model.TreatmentPlan;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.TreatmentGoalRepository;
import com.mindcare.backend.repository.TreatmentPlanRepository;
import com.mindcare.backend.repository.UserRepository;
import com.mindcare.backend.treatmentplan.dto.AddGoalRequest;
import com.mindcare.backend.treatmentplan.dto.CreatePlanRequest;
import com.mindcare.backend.treatmentplan.dto.PlanResponse;
import com.mindcare.backend.treatmentplan.dto.UpdateGoalRequest;
import com.mindcare.backend.treatmentplan.dto.UpdatePlanRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Never reachable by CLIENT or RECEPTIONIST — clinical documentation, same as notes. */
@RestController
@RequestMapping("/api/treatment-plans")
@PreAuthorize("hasAnyRole('THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
@Transactional
public class TreatmentPlanController {

    private final TreatmentPlanRepository planRepository;
    private final TreatmentGoalRepository goalRepository;
    private final UserRepository userRepository;

    public TreatmentPlanController(
            TreatmentPlanRepository planRepository,
            TreatmentGoalRepository goalRepository,
            UserRepository userRepository
    ) {
        this.planRepository = planRepository;
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('THERAPIST', 'MAINTENANCE')")
    public PlanResponse create(@Valid @RequestBody CreatePlanRequest request, @AuthenticationPrincipal User clinician) {
        User client = userRepository.findById(request.clientId())
                .orElseThrow(() -> new NoSuchElementException("Client not found"));

        TreatmentPlan plan = new TreatmentPlan(client, clinician, request.reviewDate());
        if (request.goals() != null) {
            request.goals().forEach(g -> plan.getGoals().add(new TreatmentGoal(plan, g.description(), g.interventions())));
        }
        planRepository.save(plan);
        return PlanResponse.from(plan);
    }

    @GetMapping("/client/{clientId}")
    public List<PlanResponse> forClient(@PathVariable UUID clientId) {
        return planRepository.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(PlanResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PlanResponse get(@PathVariable UUID id) {
        return PlanResponse.from(findOrThrow(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('THERAPIST', 'MAINTENANCE')")
    public PlanResponse update(@PathVariable UUID id, @RequestBody UpdatePlanRequest request, @AuthenticationPrincipal User user) {
        TreatmentPlan plan = ownPlanOrThrow(id, user);
        if (request.reviewDate() != null) plan.setReviewDate(request.reviewDate());
        if (request.status() != null) plan.setStatus(request.status());
        planRepository.save(plan);
        return PlanResponse.from(plan);
    }

    @PostMapping("/{id}/goals")
    @PreAuthorize("hasAnyRole('THERAPIST', 'MAINTENANCE')")
    public PlanResponse addGoal(@PathVariable UUID id, @Valid @RequestBody AddGoalRequest request, @AuthenticationPrincipal User user) {
        TreatmentPlan plan = ownPlanOrThrow(id, user);
        plan.getGoals().add(new TreatmentGoal(plan, request.description(), request.interventions()));
        plan.touch();
        planRepository.save(plan);
        return PlanResponse.from(plan);
    }

    @PatchMapping("/goals/{goalId}")
    @PreAuthorize("hasAnyRole('THERAPIST', 'MAINTENANCE')")
    public PlanResponse updateGoal(@PathVariable UUID goalId, @RequestBody UpdateGoalRequest request, @AuthenticationPrincipal User user) {
        TreatmentGoal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new NoSuchElementException("Goal not found"));
        TreatmentPlan plan = ownPlanOrThrow(goal.getPlan().getId(), user);

        if (request.description() != null) goal.setDescription(request.description());
        if (request.interventions() != null) goal.setInterventions(request.interventions());
        if (request.status() != null) goal.setStatus(request.status());
        plan.touch();
        goalRepository.save(goal);
        return PlanResponse.from(plan);
    }

    private TreatmentPlan findOrThrow(UUID id) {
        return planRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Plan not found"));
    }

    private TreatmentPlan ownPlanOrThrow(UUID id, User user) {
        TreatmentPlan plan = findOrThrow(id);
        if (user.getRole() == Role.THERAPIST && !plan.getResponsibleClinician().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your treatment plan");
        }
        return plan;
    }
}
