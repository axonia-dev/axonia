package dev.axonia.expertise.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.expertise.api.CuratedNamedArea;
import dev.axonia.expertise.api.NamedAreaAssignment;
import dev.axonia.expertise.api.TagCuration;
import dev.axonia.expertise.api.TagMergeSuggestion;
import dev.axonia.expertise.api.TagSplit;
import dev.axonia.expertise.api.TagUnassignment;
import dev.axonia.expertise.api.TagUsageReport;
import dev.axonia.expertise.api.VocabularyTag;
import dev.axonia.expertise.api.VocabularyTag.NamedAreaRef;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.expertise.domain.MergeSuggestion;
import dev.axonia.expertise.domain.NamedArea;
import dev.axonia.expertise.domain.NamedAreaPlacement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class CurationFacadeTest {

    private static final String SLUG = "eng";

    @Mock
    private ExpertiseCurationService tags;
    @Mock
    private NamedAreaCurationService namedAreas;

    private CurationFacade sut;

    @BeforeEach
    void setUp() {

        sut = new CurationFacade(tags, namedAreas);
    }

    @Test
    void theVocabularyNamesEachTagsMergedSpellingsAndNamedArea() {

        when(tags.vocabulary(SLUG)).thenReturn(List.of(new ExpertiseArea("kafka", List.of("kafak", "kafka"), 3),
                new ExpertiseArea("helm", List.of("helm"), 1)));
        when(namedAreas.namedAreas(SLUG)).thenReturn(List.of(new NamedArea(5L, "Messaging", List.of("kafka"))));

        assertThat(sut.vocabulary(SLUG)).containsExactly(
                new VocabularyTag("kafka", List.of("kafak"), 3, new NamedAreaRef(5L, "Messaging")),
                new VocabularyTag("helm", List.of(), 1, null));
    }

    @Test
    void eachSuggestionCarriesWhatBothTagsHoldUp() {

        when(tags.suggestions(SLUG)).thenReturn(List.of(new MergeSuggestion("kafka", "kafak", 0.8)));
        when(tags.vocabulary(SLUG)).thenReturn(List.of(new ExpertiseArea("kafka", List.of("kafka"), 3)));
        var kafka = new TagUsageReport(4, 1, 0, 3, 0);
        var kafak = new TagUsageReport(1, 0, 0, 0, 0);
        when(tags.usages(SLUG, List.of("kafka", "kafak"))).thenReturn(Map.of("kafka", kafka, "kafak", kafak));

        assertThat(sut.suggestions(SLUG))
                .containsExactly(new TagMergeSuggestion(new TagMergeSuggestion.Side("kafka", List.of(), 3, kafka),
                        new TagMergeSuggestion.Side("kafak", List.of(), 0, kafak), 0.8));
    }

    @Test
    void suggestionsStopAtTheLimit_soTheirUsageStaysBounded() {

        var pairs = java.util.stream.IntStream.range(0, TagCuration.SUGGESTION_LIMIT + 5)
                .mapToObj(i -> new MergeSuggestion("tag-" + i, "tag-" + i + "x", 0.9)).toList();
        when(tags.suggestions(SLUG)).thenReturn(pairs);
        when(tags.vocabulary(SLUG)).thenReturn(List.of());
        when(tags.usages(org.mockito.ArgumentMatchers.eq(SLUG), anyCollection())).thenReturn(Map.of());

        assertThat(sut.suggestions(SLUG)).hasSize(TagCuration.SUGGESTION_LIMIT);
    }

    @Test
    void noSuggestionsReadNothingElse() {

        when(tags.suggestions(SLUG)).thenReturn(List.of());

        assertThat(sut.suggestions(SLUG)).isEmpty();
        verify(tags, never()).usages(org.mockito.ArgumentMatchers.eq(SLUG), anyCollection());
    }

    @Test
    void aMergeAnswersTheCanonicalWithEverySpellingMergedIntoIt() {

        when(tags.vocabulary(SLUG))
                .thenReturn(List.of(new ExpertiseArea("kubernetes", List.of("k8s", "kube", "kubernetes"), 2)));
        when(namedAreas.namedAreas(SLUG)).thenReturn(List.of());

        var merged = sut.merge(SLUG, List.of("k8s", "kube"), "Kubernetes");

        verify(tags).mergeMany(SLUG, List.of("k8s", "kube"), "Kubernetes");
        assertThat(merged).isEqualTo(new VocabularyTag("kubernetes", List.of("k8s", "kube"), 2, null));
    }

    @Test
    void aSplitAnswersBothTags() {

        when(tags.split(SLUG, "k8s")).thenReturn(Optional.of("kubernetes"));
        when(tags.vocabulary(SLUG)).thenReturn(List.of(new ExpertiseArea("k8s", List.of("k8s"), 1),
                new ExpertiseArea("kubernetes", List.of("kubernetes"), 2)));
        when(namedAreas.namedAreas(SLUG)).thenReturn(List.of());

        assertThat(sut.split(SLUG, "k8s")).isEqualTo(new TagSplit(new VocabularyTag("k8s", List.of(), 1, null),
                new VocabularyTag("kubernetes", List.of(), 2, null)));
    }

    @Test
    void aSplitOfATagNotMergedHasNoFormerCanonical() {

        when(tags.split(SLUG, "helm")).thenReturn(Optional.empty());
        when(tags.vocabulary(SLUG)).thenReturn(List.of());
        when(namedAreas.namedAreas(SLUG)).thenReturn(List.of());

        assertThat(sut.split(SLUG, "helm")).isEqualTo(new TagSplit(VocabularyTag.bare("helm"), null));
    }

    @Test
    void aRenameAnswersTheTagUnderItsNewName() {

        when(tags.vocabulary(SLUG)).thenReturn(List.of(new ExpertiseArea("event-streaming", List.of(), 0)));
        when(namedAreas.namedAreas(SLUG)).thenReturn(List.of());

        var renamed = sut.rename(SLUG, "kafka", "Event Streaming");

        verify(tags).rename(SLUG, "kafka", "Event Streaming");
        assertThat(renamed).isEqualTo(VocabularyTag.bare("event-streaming"));
    }

    @Test
    void theRemainingTagVerbsPassThrough() {

        when(tags.usage(SLUG, "kafka")).thenReturn(new TagUsageReport(1, 0, 0, 0, 0));

        sut.dismiss(SLUG, "kafka", "kafak");
        sut.undismiss(SLUG, "kafka", "kafak");
        sut.delete(SLUG, "flink");

        assertThat(sut.usage(SLUG, "kafka")).isEqualTo(new TagUsageReport(1, 0, 0, 0, 0));
        verify(tags).dismiss(SLUG, "kafka", "kafak");
        verify(tags).undismiss(SLUG, "kafka", "kafak");
        verify(tags).delete(SLUG, "flink");
    }

    @Test
    void namedAreasMapToTheirPublicShape() {

        var messaging = new NamedArea(5L, "Messaging", List.of("kafka"));
        var curated = new CuratedNamedArea(5L, "Messaging", List.of("kafka"));
        when(namedAreas.namedAreas(SLUG)).thenReturn(List.of(messaging));
        when(namedAreas.createNamedArea(SLUG, "Messaging")).thenReturn(messaging);
        when(namedAreas.renameNamedArea(SLUG, 5L, "Messaging")).thenReturn(messaging);
        when(namedAreas.deleteNamedArea(SLUG, 5L)).thenReturn(messaging);
        when(namedAreas.removeFromNamedArea(SLUG, "spring"))
                .thenReturn(new NamedAreaCurationService.TagRemoval("spring", Optional.of(messaging)));

        assertThat(sut.namedAreas(SLUG)).containsExactly(curated);
        assertThat(sut.create(SLUG, "Messaging")).isEqualTo(curated);
        assertThat(sut.rename(SLUG, 5L, "Messaging")).isEqualTo(curated);
        assertThat(sut.delete(SLUG, 5L)).isEqualTo(curated);
        assertThat(sut.unassign(SLUG, "spring")).isEqualTo(new TagUnassignment("spring", Optional.of(curated)));
    }

    @Test
    void anAssignmentReportsEveryMove() {

        when(namedAreas.placeAllInNamedArea(SLUG, 6L, List.of("kafka", "spring")))
                .thenReturn(new NamedAreaPlacement(new NamedArea(6L, "Backend", List.of("kafka", "spring")),
                        List.of(new NamedAreaPlacement.Move("kafka", 5L, "Messaging"))));

        assertThat(sut.assign(SLUG, 6L, List.of("kafka", "spring")))
                .isEqualTo(new NamedAreaAssignment(new CuratedNamedArea(6L, "Backend", List.of("kafka", "spring")),
                        List.of(new NamedAreaAssignment.Move("kafka", 5L, "Messaging"))));
    }
}
