package dev.axonia.mentoring.adapter.out.expertise;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.expertise.api.CommunityVocabulary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class MentoringVocabularyAdapterTest {

    @Mock
    private CommunityVocabulary expertiseVocabulary;

    @Test
    void readsTheGuildsAreasThroughExpertise() {

        // given
        var sut = new MentoringVocabularyAdapter(expertiseVocabulary);
        var areas = new CommunityAreas(Map.of("k8s", "kubernetes"));
        when(expertiseVocabulary.areasOf(7L)).thenReturn(areas);
        when(expertiseVocabulary.visibleTags("platform")).thenReturn(List.of("kafka", "kubernetes"));

        // when / then
        assertThat(sut.areasOf(7L)).isSameAs(areas);
        assertThat(sut.areasIn("platform")).containsExactlyInAnyOrder("kafka", "kubernetes");
    }

    @Test
    void sharesTheVocabularyLock() {

        // given
        var sut = new MentoringVocabularyAdapter(expertiseVocabulary);

        // when
        sut.shareLock("org-1", 7L);

        // then
        verify(expertiseVocabulary).shareLock("org-1", 7L);
    }

    @Test
    void aLabelIsNormalizedAsATag() {

        // given
        var sut = new MentoringVocabularyAdapter(expertiseVocabulary);

        // when
        var actual = sut.normalized("  Event Sourcing! ");

        // then
        assertThat(actual).isEqualTo("event-sourcing");
    }
}
