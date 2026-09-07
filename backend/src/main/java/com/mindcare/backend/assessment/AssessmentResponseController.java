package com.mindcare.backend.assessment;

import com.mindcare.backend.assessment.dto.AssignAssessmentRequest;
import com.mindcare.backend.assessment.dto.ResponseDetail;
import com.mindcare.backend.assessment.dto.ResponseSummary;
import com.mindcare.backend.assessment.dto.SubmitAnswersRequest;
import com.mindcare.backend.model.AssessmentAnswer;
import com.mindcare.backend.model.AssessmentQuestion;
import com.mindcare.backend.model.AssessmentResponse;
import com.mindcare.backend.model.AssessmentTemplate;
import com.mindcare.backend.model.Role;
import com.mindcare.backend.model.User;
import com.mindcare.backend.repository.AssessmentResponseRepository;
import com.mindcare.backend.repository.AssessmentTemplateRepository;
import com.mindcare.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Assigning templates to clients, and clients completing them. */
@RestController
@RequestMapping("/api/assessment-responses")
@Transactional
public class AssessmentResponseController {

    private final AssessmentResponseRepository responseRepository;
    private final AssessmentTemplateRepository templateRepository;
    private final UserRepository userRepository;

    public AssessmentResponseController(
            AssessmentResponseRepository responseRepository,
            AssessmentTemplateRepository templateRepository,
            UserRepository userRepository
    ) {
        this.responseRepository = responseRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
    public ResponseSummary assign(@Valid @RequestBody AssignAssessmentRequest request, @AuthenticationPrincipal User assignedBy) {
        AssessmentTemplate template = templateRepository.findById(request.templateId())
                .orElseThrow(() -> new NoSuchElementException("Template not found"));
        User client = userRepository.findById(request.clientId())
                .orElseThrow(() -> new NoSuchElementException("Client not found"));
        if (client.getRole() != Role.CLIENT) {
            throw new IllegalArgumentException("Assessments can only be assigned to clients");
        }

        AssessmentResponse response = new AssessmentResponse(template, client, assignedBy);
        responseRepository.save(response);
        return ResponseSummary.from(response);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public List<ResponseSummary> mine(@AuthenticationPrincipal User client) {
        return responseRepository.findByClientIdOrderByAssignedAtDesc(client.getId()).stream()
                .map(ResponseSummary::from).toList();
    }

    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAnyRole('THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
    public List<ResponseSummary> forClient(@PathVariable UUID clientId) {
        return responseRepository.findByClientIdOrderByAssignedAtDesc(clientId).stream()
                .map(ResponseSummary::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT', 'THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
    public ResponseDetail get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        AssessmentResponse response = findOrThrow(id);
        if (user.getRole() == Role.CLIENT && !response.getClient().getId().equals(user.getId())) {
            throw new AccessDeniedException("Not your assessment");
        }
        return ResponseDetail.from(response);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseDetail submit(@PathVariable UUID id, @Valid @RequestBody SubmitAnswersRequest request, @AuthenticationPrincipal User client) {
        AssessmentResponse response = findOrThrow(id);
        if (!response.getClient().getId().equals(client.getId())) {
            throw new AccessDeniedException("Not your assessment");
        }
        if (response.isCompleted()) {
            throw new IllegalArgumentException("This assessment has already been submitted");
        }

        Map<UUID, AssessmentQuestion> questionsById = new HashMap<>();
        for (AssessmentQuestion q : response.getTemplate().getQuestions()) {
            questionsById.put(q.getId(), q);
        }

        int total = 0;
        for (SubmitAnswersRequest.AnswerInput input : request.answers()) {
            AssessmentQuestion question = questionsById.get(input.questionId());
            if (question == null) {
                throw new IllegalArgumentException("That question doesn't belong to this assessment");
            }
            if (input.score() < question.getMinScore() || input.score() > question.getMaxScore()) {
                throw new IllegalArgumentException("Score out of range for: " + question.getQuestionText());
            }
            response.getAnswers().add(new AssessmentAnswer(response, question, input.score()));
            total += input.score();
        }

        if (response.getAnswers().size() != response.getTemplate().getQuestions().size()) {
            throw new IllegalArgumentException("All questions must be answered");
        }

        response.complete(total, response.getTemplate().interpret(total));
        responseRepository.save(response);
        return ResponseDetail.from(response);
    }

    private AssessmentResponse findOrThrow(UUID id) {
        return responseRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Assessment not found"));
    }
}
