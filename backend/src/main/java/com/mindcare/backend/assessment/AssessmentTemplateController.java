package com.mindcare.backend.assessment;

import com.mindcare.backend.assessment.dto.CreateTemplateRequest;
import com.mindcare.backend.assessment.dto.TemplateResponse;
import com.mindcare.backend.model.AssessmentQuestion;
import com.mindcare.backend.model.AssessmentTemplate;
import com.mindcare.backend.repository.AssessmentTemplateRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Authoring the reusable questionnaire definitions themselves. */
@RestController
@RequestMapping("/api/assessment-templates")
@PreAuthorize("hasAnyRole('THERAPIST', 'CLINICAL_SUPERVISOR', 'MAINTENANCE')")
@Transactional
public class AssessmentTemplateController {

    private final AssessmentTemplateRepository templateRepository;

    public AssessmentTemplateController(AssessmentTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @GetMapping
    public List<TemplateResponse> list() {
        return templateRepository.findByActiveTrue().stream().map(TemplateResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TemplateResponse create(@Valid @RequestBody CreateTemplateRequest request) {
        AssessmentTemplate template = new AssessmentTemplate(request.name(), request.description(), request.scoreBands());
        int index = 0;
        for (CreateTemplateRequest.QuestionInput q : request.questions()) {
            template.getQuestions().add(new AssessmentQuestion(template, index++, q.questionText(), q.minScore(), q.maxScore()));
        }
        templateRepository.save(template);
        return TemplateResponse.from(template);
    }
}
