package com.mindcare.backend.bootstrap;

import com.mindcare.backend.model.AssessmentQuestion;
import com.mindcare.backend.model.AssessmentTemplate;
import com.mindcare.backend.repository.AssessmentTemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the PHQ-9 (a standard, public-domain depression screening
 * questionnaire) so the assessment engine has a real, clinically validated
 * instrument to assign out of the box, rather than launching empty.
 */
@Component
public class AssessmentTemplateSeeder implements CommandLineRunner {

    private static final String[] PHQ9_QUESTIONS = {
            "Little interest or pleasure in doing things",
            "Feeling down, depressed, or hopeless",
            "Trouble falling or staying asleep, or sleeping too much",
            "Feeling tired or having little energy",
            "Poor appetite or overeating",
            "Feeling bad about yourself — or that you are a failure, or have let yourself or your family down",
            "Trouble concentrating on things, such as reading or watching television",
            "Moving or speaking so slowly that others could have noticed, or the opposite — being fidgety or restless",
            "Thoughts that you would be better off dead, or of hurting yourself in some way",
    };

    private final AssessmentTemplateRepository templateRepository;

    public AssessmentTemplateSeeder(AssessmentTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    public void run(String... args) {
        boolean alreadySeeded = templateRepository.findByActiveTrue().stream()
                .anyMatch(t -> t.getName().equals("PHQ-9"));
        if (alreadySeeded) {
            return;
        }

        AssessmentTemplate template = new AssessmentTemplate(
                "PHQ-9",
                "Over the last 2 weeks, how often have you been bothered by any of the following problems? "
                        + "0 = Not at all, 1 = Several days, 2 = More than half the days, 3 = Nearly every day.",
                "0-4:Minimal;5-9:Mild;10-14:Moderate;15-19:Moderately severe;20-27:Severe"
        );
        for (int i = 0; i < PHQ9_QUESTIONS.length; i++) {
            template.getQuestions().add(new AssessmentQuestion(template, i, PHQ9_QUESTIONS[i], 0, 3));
        }
        templateRepository.save(template);
    }
}
