package dev.axonia.mentoring.adapter.out.learning;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.learning.api.LearningIntents;
import dev.axonia.mentoring.application.port.out.MenteeAreas;
import lombok.RequiredArgsConstructor;

@Component("mentoringMenteeAreasAdapter")
@RequiredArgsConstructor
class MenteeAreasAdapter implements MenteeAreas {

    private final LearningIntents learningIntents;

    @Override
    public Map<String, List<String>> wantedIn(String slug) {

        return learningIntents.learnersIn(slug);
    }
}
