package dev.axonia.mentoring.adapter.out.learning;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.learning.api.LearningIntents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class MenteeAreasAdapterTest {

    @Mock
    private LearningIntents learningIntents;

    @Test
    void theWantedAreasAreLearningsLearners() {

        // given
        when(learningIntents.learnersIn("platform")).thenReturn(Map.of("ana", List.of("rust")));

        // when
        var actual = new MenteeAreasAdapter(learningIntents).wantedIn("platform");

        // then
        assertThat(actual).containsExactly(Map.entry("ana", List.of("rust")));
    }
}
