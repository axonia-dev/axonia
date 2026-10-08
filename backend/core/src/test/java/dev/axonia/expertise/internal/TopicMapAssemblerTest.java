package dev.axonia.expertise.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import dev.axonia.expertise.Declaration;
import dev.axonia.expertise.NamedArea;
import dev.axonia.expertise.TopicMap;
import dev.axonia.expertise.internal.ExpertiseQueryRepository.UserTagAggregate;

import static org.assertj.core.api.Assertions.assertThat;

class TopicMapAssemblerTest {

    private static final Instant T = Instant.parse("2026-05-01T00:00:00Z");

    private static UserTagAggregate row(String user, String tag, int presented) {

        return new UserTagAggregate(user, tag, presented, 0, 0, T);
    }

    private static Declaration declaration(String user, String tag) {

        return Declaration.builder().communityId(1L).userId(user).tag(tag).declaredAt(T).build();
    }

    private static TopicMap assemble(List<UserTagAggregate> rows, List<Declaration> declarations,
            Map<String, String> aliasMap, UnaryOperator<String> nameOf) {

        return TopicMapAssembler.assemble(TopicMapAssembler.Sources.builder().rows(rows).declarations(declarations)
                .namedAreas(List.of()).aliasMap(aliasMap).nameOf(nameOf).build());
    }

    private static TopicMap withNamedAreas(List<UserTagAggregate> rows, Map<String, String> aliasMap,
            NamedArea... namedAreas) {

        return TopicMapAssembler.assemble(TopicMapAssembler.Sources.builder().rows(rows).declarations(List.of())
                .namedAreas(List.of(namedAreas)).aliasMap(aliasMap).nameOf(name -> name).build());
    }

    @Test
    void aNodeCountsDistinctPractitionersAndTotalSignals() {

        var rows = List.of(row("ana", "kafka", 4), row("bruno", "kafka", 3));

        var map = assemble(rows, List.of(), Map.of(), name -> name);

        assertThat(map.topics()).singleElement().satisfies(t -> {
            assertThat(t.canonicalTag()).isEqualTo("kafka");
            assertThat(t.practitionerCount()).isEqualTo(2);
            assertThat(t.signalCount()).isEqualTo(7);
        });
    }

    @Test
    void aVariantFoldsIntoItsCanonicalNodeRatherThanStandingBeside() {

        var rows = List.of(row("ana", "kafka", 1), row("bruno", "apache-kafka", 1));

        var map = assemble(rows, List.of(), Map.of("apache-kafka", "kafka"), name -> name);

        assertThat(map.topics()).singleElement().satisfies(t -> {
            assertThat(t.canonicalTag()).isEqualTo("kafka");
            assertThat(t.practitionerCount()).isEqualTo(2);
        });
    }

    @Test
    void onePersonPractisingThreeTopicsYieldsThreeLinksOfWeightOne() {

        var rows = List.of(row("ana", "kafka", 1), row("ana", "ddd", 1), row("ana", "testing", 1));

        var map = assemble(rows, List.of(), Map.of(), name -> name);

        assertThat(map.links()).hasSize(3).allSatisfy(l -> assertThat(l.weight()).isEqualTo(1));
    }

    @Test
    void twoPeopleSharingAPairMakeThatLinkWeightTwo() {

        var rows = List.of(row("ana", "kafka", 1), row("ana", "debezium", 1), row("bruno", "kafka", 1),
                row("bruno", "debezium", 1));

        var map = assemble(rows, List.of(), Map.of(), name -> name);

        assertThat(map.links()).singleElement().satisfies(l -> {
            assertThat(l.a()).isEqualTo("debezium");
            assertThat(l.b()).isEqualTo("kafka");
            assertThat(l.weight()).isEqualTo(2);
        });
    }

    @Test
    void aVariantsLinksMergeIntoItsCanonicalRatherThanDoubling() {

        // ana practises kafka and postgres; bruno practises the variant and postgres
        var rows = List.of(row("ana", "kafka", 1), row("ana", "postgres", 1), row("bruno", "apache-kafka", 1),
                row("bruno", "postgres", 1));

        var map = assemble(rows, List.of(), Map.of("apache-kafka", "kafka"), name -> name);

        assertThat(map.links()).singleElement().satisfies(l -> assertThat(l.weight()).isEqualTo(2));
    }

    @Test
    void theTopHolderIsTheLargestShareOfSignalsAndIsNamed() {

        // zoe holds the larger share but sorts last alphabetically and second in insertion order, so a selector
        // that picks by key order instead of by value would wrongly land on ana.
        var rows = List.of(row("zoe", "kubernetes", 9), row("ana", "kubernetes", 1));

        var map = assemble(rows, List.of(), Map.of(), user -> "Name of " + user);

        assertThat(map.topics()).singleElement().satisfies(t -> {
            assertThat(t.topHolderShare()).isEqualTo(9d / 10d);
            assertThat(t.topHolderName()).isEqualTo("Name of zoe");
        });
    }

    @Test
    void lastActivityIsTheMostRecentAcrossEveryPractitioner() {

        var older = new UserTagAggregate("ana", "slo", 1, 0, 0, Instant.parse("2025-01-01T00:00:00Z"));
        var newer = new UserTagAggregate("bruno", "slo", 1, 0, 0, Instant.parse("2026-02-02T00:00:00Z"));

        var map = assemble(List.of(older, newer), List.of(), Map.of(), name -> name);

        assertThat(map.topics()).singleElement()
                .satisfies(t -> assertThat(t.lastActivityAt()).isEqualTo(Instant.parse("2026-02-02T00:00:00Z")));
    }

    @Test
    void aPersonPractisingOneTopicMakesNoLink() {

        var map = assemble(List.of(row("mira", "accessibility", 2)), List.of(), Map.of(), name -> name);

        assertThat(map.links()).isEmpty();
    }

    @Test
    void aCompletedOnlyPractitionerIsKeptWithZeroShareAndStillMovesLastActivity() {

        // ana has only a COMPLETED signal for "slo" (presented = contributed = offered = 0), which the query
        // deliberately never buckets (TierPolicy.evidenceOf); she still counts as a practitioner of "slo" and her
        // occurrence can still be the most recent one. bruno's row is on an unrelated tag, so "slo"'s total stays 0.
        var completedOnly = new UserTagAggregate("ana", "slo", 0, 0, 0, Instant.parse("2026-06-01T00:00:00Z"));
        var unrelated = row("bruno", "kafka", 1);

        var map = assemble(List.of(completedOnly, unrelated), List.of(), Map.of(), name -> name);

        var slo = map.topics().stream().filter(t -> t.canonicalTag().equals("slo")).findFirst().orElseThrow();
        assertThat(slo.practitionerCount()).isEqualTo(1);
        assertThat(slo.signalCount()).isEqualTo(0);
        assertThat(slo.topHolderShare()).isEqualTo(0d);
        assertThat(slo.lastActivityAt()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
    }

    @Test
    void aPersonHoldingBothAVariantAndItsCanonicalMakesNoSelfLink() {

        var rows = List.of(row("ana", "kafka", 1), row("ana", "apache-kafka", 1), row("ana", "postgres", 1));

        var map = assemble(rows, List.of(), Map.of("apache-kafka", "kafka"), name -> name);

        assertThat(map.links()).singleElement().satisfies(l -> {
            assertThat(l.a()).isEqualTo("kafka");
            assertThat(l.b()).isEqualTo("postgres");
        });
        var kafka = map.topics().stream().filter(t -> t.canonicalTag().equals("kafka")).findFirst().orElseThrow();
        assertThat(kafka.practitionerCount()).isEqualTo(1);
        assertThat(kafka.signalCount()).isEqualTo(2);
    }

    @Test
    void anAreaCarriedOnlyByDeclarationsIsOnTheMapWithNoHolderAndNoActivity() {

        var map = assemble(List.of(), List.of(declaration("ana", "graphql")), Map.of(), name -> name);

        assertThat(map.topics()).singleElement().satisfies(t -> {
            assertThat(t.canonicalTag()).isEqualTo("graphql");
            assertThat(t.openToQuestionsCount()).isEqualTo(1);
            assertThat(t.practitionerCount()).isZero();
            assertThat(t.signalCount()).isZero();
            assertThat(t.topHolderShare()).isZero();
            assertThat(t.topHolderName()).isNull();
            assertThat(t.lastActivityAt()).isNull();
        });
    }

    @Test
    void aPractitionerWhoAlsoDeclaredIsCountedInBothTallies() {

        var map = assemble(List.of(row("ana", "kafka", 2)), List.of(declaration("ana", "kafka")), Map.of(),
                name -> name);

        assertThat(map.topics()).singleElement().satisfies(t -> {
            assertThat(t.practitionerCount()).isEqualTo(1);
            assertThat(t.openToQuestionsCount()).isEqualTo(1);
            assertThat(t.topHolderName()).isEqualTo("ana");
        });
    }

    @Test
    void aDeclaredVariantFoldsIntoItsCanonicalNode() {

        var map = assemble(List.of(row("ana", "kafka", 1)), List.of(declaration("bruno", "apache-kafka")),
                Map.of("apache-kafka", "kafka"), name -> name);

        assertThat(map.topics()).singleElement().satisfies(t -> {
            assertThat(t.canonicalTag()).isEqualTo("kafka");
            assertThat(t.openToQuestionsCount()).isEqualTo(1);
        });
    }

    @Test
    void oneDeclarerCarryingTwoAreasLinksThemAsPracticeWould() {

        var map = assemble(List.of(), List.of(declaration("ana", "graphql"), declaration("ana", "grpc")), Map.of(),
                name -> name);

        assertThat(map.links()).singleElement().satisfies(l -> {
            assertThat(l.a()).isEqualTo("graphql");
            assertThat(l.b()).isEqualTo("grpc");
            assertThat(l.weight()).isEqualTo(1);
        });
    }

    @Test
    void aDeclarationOnAnAreaThePersonAlreadyPractisesAddsNoSecondLink() {

        var map = assemble(List.of(row("ana", "kafka", 1), row("ana", "ddd", 1)), List.of(declaration("ana", "kafka")),
                Map.of(), name -> name);

        assertThat(map.links()).hasSize(1);
    }

    @Test
    void withNoNamedAreas_theMapCarriesAnEmptyListRatherThanNull() {

        var map = assemble(List.of(row("ana", "kafka", 1)), List.of(), Map.of(), name -> name);

        assertThat(map.namedAreas()).isNotNull().isEmpty();
    }

    @Test
    void aStoredTagFoldsThroughTheAliasOntoTheCanonicalTheMapDraws() {

        var map = withNamedAreas(List.of(row("ana", "kafka", 1)), Map.of("apache-kafka", "kafka"),
                new NamedArea(1L, "Streaming", List.of("apache-kafka")));

        assertThat(map.namedAreas()).containsExactly(new NamedArea(1L, "Streaming", List.of("kafka")));
    }

    @Test
    void aTagTheMapDoesNotDrawIsDropped_andTheNamedAreaStaysWithWhatIsLeft() {

        var map = withNamedAreas(List.of(row("ana", "kafka", 1)), Map.of(),
                new NamedArea(1L, "Streaming", List.of("flink", "kafka")),
                new NamedArea(2L, "Frontend", List.of("react")));

        assertThat(map.namedAreas()).containsExactly(new NamedArea(1L, "Streaming", List.of("kafka")),
                new NamedArea(2L, "Frontend", List.of()));
    }

    @Test
    void aCanonicalsOwnRowWinsOverAFoldedRowInAnotherNamedArea() {

        var map = withNamedAreas(List.of(row("ana", "kafka", 1)), Map.of("apache-kafka", "kafka"),
                new NamedArea(1L, "Streaming", List.of("apache-kafka")),
                new NamedArea(2L, "Messaging", List.of("kafka")));

        assertThat(map.namedAreas()).containsExactly(new NamedArea(1L, "Streaming", List.of()),
                new NamedArea(2L, "Messaging", List.of("kafka")));
    }

    @Test
    void amongFoldedRowsOnly_theLowestNamedAreaIdWins() {

        var map = withNamedAreas(List.of(row("ana", "kafka", 1)),
                Map.of("apache-kafka", "kafka", "confluent-kafka", "kafka"),
                new NamedArea(5L, "Messaging", List.of("apache-kafka")),
                new NamedArea(3L, "Streaming", List.of("confluent-kafka")));

        assertThat(map.namedAreas()).containsExactly(new NamedArea(5L, "Messaging", List.of()),
                new NamedArea(3L, "Streaming", List.of("kafka")));
    }

    @Test
    void aVariantAndItsCanonicalInOneNamedAreaCollapseToOneTag() {

        var map = withNamedAreas(List.of(row("ana", "kafka", 1), row("ana", "ddd", 1)), Map.of("apache-kafka", "kafka"),
                new NamedArea(1L, "Streaming", List.of("apache-kafka", "ddd", "kafka")));

        assertThat(map.namedAreas()).containsExactly(new NamedArea(1L, "Streaming", List.of("ddd", "kafka")));
    }
}
