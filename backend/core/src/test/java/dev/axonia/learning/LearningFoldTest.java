package dev.axonia.learning;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.axonia.expertise.CommunityAreas;

import static org.assertj.core.api.Assertions.assertThat;

class LearningFoldTest {

    private static final Instant EARLY = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATE = Instant.parse("2026-06-01T00:00:00Z");

    private final CommunityAreas areas = new CommunityAreas(Map.of("k8s", "kubernetes"));

    @Test
    void heldAreasFoldSpellingsKeepingTheEarliestDateInDateOrder() {

        var held = LearningFold.held(List.of(intent("alice", "kafka", LATE), intent("alice", "kubernetes", LATE),
                intent("alice", "k8s", EARLY)), areas);

        assertThat(held).containsExactly(new HeldArea("kubernetes", EARLY), new HeldArea("kafka", LATE));
    }

    @Test
    void heldAreasOfOneDateAreInNameOrder() {

        var held = LearningFold.held(List.of(intent("alice", "rust", EARLY), intent("alice", "helm", EARLY)), areas);

        assertThat(held).containsExactly(new HeldArea("helm", EARLY), new HeldArea("rust", EARLY));
    }

    @Test
    void wantedIsSortedByLearnersThenNameWithZeroSupply() {

        var wanted = LearningFold.wanted(List.of(intent("alice", "kafka", EARLY), intent("bob", "rust", EARLY),
                intent("carol", "rust", EARLY), intent("dave", "helm", EARLY), intent("alice", "k8s", EARLY),
                intent("bob", "kubernetes", EARLY), intent("bob", "k8s", LATE)), areas, Map.of("kafka", 3));

        assertThat(wanted).containsExactly(new WantedArea("kubernetes", 2, 0), new WantedArea("rust", 2, 0),
                new WantedArea("helm", 1, 0), new WantedArea("kafka", 1, 3));
    }

    @Test
    void learnersOfAnAreaAreEachMemberOnceAtTheirEarliestDateEarliestFirst() {

        var learners = LearningFold.learners(List.of(intent("bob", "kubernetes", LATE), intent("alice", "k8s", LATE),
                intent("alice", "kubernetes", EARLY), intent("carol", "kafka", EARLY)), areas, "kubernetes");

        assertThat(learners).containsExactly(Map.entry("alice", EARLY), Map.entry("bob", LATE));
    }

    @Test
    void learnersOfOneDateAreInIdOrder() {

        var learners = LearningFold.learners(List.of(intent("bob", "kafka", EARLY), intent("alice", "kafka", EARLY)),
                areas, "kafka");

        assertThat(learners).containsExactly(Map.entry("alice", EARLY), Map.entry("bob", EARLY));
    }

    private static LearningIntent intent(String user, String tag, Instant at) {

        return LearningIntent.builder().communityId(7L).userId(user).tag(tag).declaredAt(at).build();
    }
}
