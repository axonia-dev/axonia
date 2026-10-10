package dev.axonia.expertise.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.application.port.out.ExpertiseQueryRepository;
import dev.axonia.expertise.application.port.out.NamedAreaRepository;
import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.PersonDirectory;
import dev.axonia.expertise.application.port.out.SourceReachResolver;
import dev.axonia.expertise.application.port.out.TagAliasRepository;
import dev.axonia.expertise.domain.Declaration;
import dev.axonia.expertise.domain.EvidenceItem;
import dev.axonia.expertise.domain.ExpertiseSignal;
import dev.axonia.expertise.domain.ExpertiseSignalMother;
import dev.axonia.expertise.domain.NamedArea;
import dev.axonia.expertise.domain.Person;
import dev.axonia.expertise.domain.SignalType;
import dev.axonia.expertise.domain.SourceRef;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.expertise.domain.TopicNode;
import dev.axonia.expertise.domain.UserTagAggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class ExpertiseMapServiceTest {

    private static final String SLUG = "eng";
    private static final Instant EARLY = Instant.parse("2026-09-01T12:00:00Z");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private ExpertiseQueryRepository expertiseQueryRepository;
    @Mock
    private TagAliasRepository tagAliasRepository;
    @Mock
    private DeclarationRepository declarationRepository;
    @Mock
    private EvidenceEnricher evidenceEnricher;
    @Mock
    private SourceReachResolver sourceReachResolver;
    @Mock
    private NamedAreaRepository namedAreaRepository;

    private ExpertiseMapService sut;

    @BeforeEach
    void setUp() {

        sut = new ExpertiseMapService(organizationContext, communityAccess, personDirectory, expertiseQueryRepository,
                tagAliasRepository, declarationRepository, evidenceEnricher, sourceReachResolver, namedAreaRepository);
        when(organizationContext.organizationId()).thenReturn("org-1");
        when(communityAccess.communityIdForSlug(SLUG)).thenReturn(1L);
        when(tagAliasRepository.aliasMap("org-1", 1L)).thenReturn(Map.of());
        when(declarationRepository.allVisibleIn("org-1", 1L)).thenReturn(List.of());
        when(personDirectory.resolve(any())).thenReturn(Map.of());
        when(expertiseQueryRepository.userTagAggregates("org-1", 1L)).thenReturn(List.of());
        when(expertiseQueryRepository.visibleSignals(eq("org-1"), eq(1L), anyInt())).thenReturn(List.of());
        when(evidenceEnricher.enrichItems(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sourceReachResolver.reachFor(any())).thenReturn(Map.of());
        when(namedAreaRepository.findAll("org-1", 1L)).thenReturn(List.of());
    }

    // -------------------------------------------------------------------------
    // contributions()
    // -------------------------------------------------------------------------

    @Test
    void contributions_liftsEachAuthorOutOfTheRows() {

        givenVisibleSignals(signal(1L, "alice", "kafka", SourceType.SLOT, 10L),
                signal(2L, "alice", "kafka", SourceType.ARTIFACT, 20L));

        var result = sut.contributions(SLUG);

        assertThat(result.people()).extracting(MapPerson::userId).containsExactly("alice");
        assertThat(result.items()).hasSize(2);
    }

    @Test
    void contributions_namesEachAuthorFromTheDirectory() {

        givenVisibleSignals(signal(1L, "alice", "kafka", SourceType.SLOT, 10L));
        givenDirectory(Map.of("alice", new Person("alice", "Alice Ng", "https://avatars/alice.png", "Platform")));

        assertThat(sut.contributions(SLUG).people())
                .containsExactly(new MapPerson("alice", "Alice Ng", "https://avatars/alice.png"));
    }

    @Test
    void contributions_foldsTagOntoItsCanonicalArea() {

        givenAliases(Map.of("apache-kafka", "kafka"));
        givenVisibleSignals(signal(1L, "alice", "apache-kafka", SourceType.SLOT, 10L));

        assertThat(sut.contributions(SLUG).items()).extracting(MapContribution::canonicalTag).containsExactly("kafka");
    }

    @Test
    void contributions_attachesReachToASlot() {

        givenVisibleSignals(signal(1L, "alice", "kafka", SourceType.SLOT, 10L));
        givenReach(Map.of(new SourceRef(SourceType.SLOT, 10L), 42));

        assertThat(sut.contributions(SLUG).items()).extracting(MapContribution::reach).containsExactly(42);
    }

    @Test
    void contributions_asksTheResolverForEachSourceOnce() {

        givenVisibleSignals(signal(1L, "alice", "kafka", SourceType.SLOT, 10L),
                signal(2L, "bruno", "kafka", SourceType.SLOT, 10L));

        sut.contributions(SLUG);

        verify(sourceReachResolver).reachFor(List.of(new SourceRef(SourceType.SLOT, 10L)));
    }

    @Test
    void contributions_leavesReachNullWhenUnknown() {

        givenVisibleSignals(signal(1L, "alice", "kafka", SourceType.ARTIFACT, 20L));

        assertThat(sut.contributions(SLUG).items()).extracting(MapContribution::reach).containsOnlyNulls();
    }

    @Test
    void contributions_carriesTheEnrichedEvidence() {

        var signal = signal(1L, "alice", "kafka", SourceType.SLOT, 10L);
        givenVisibleSignals(signal);
        when(evidenceEnricher.enrichItems(any(), any()))
                .thenReturn(List.of(EvidenceItem.builder().signalType(SignalType.PRESENTED).sourceType(SourceType.SLOT)
                        .sourceId(10L).label("Kafka at scale").build()));

        assertThat(sut.contributions(SLUG).items()).singleElement().satisfies(item -> {
            assertThat(item.signalId()).isEqualTo(1L);
            assertThat(item.userId()).isEqualTo("alice");
            assertThat(item.evidence().label()).isEqualTo("Kafka at scale");
        });
    }

    @Test
    void contributions_handsTheEnricherTheSignalsOwnFacts() {

        givenVisibleSignals(signal(1L, "alice", "kafka", SourceType.SLOT, 10L));

        assertThat(sut.contributions(SLUG).items()).singleElement().extracting(MapContribution::evidence)
                .extracting(EvidenceItem::signalType, EvidenceItem::sourceType, EvidenceItem::sourceId,
                        EvidenceItem::occurredAt)
                .containsExactly(SignalType.PRESENTED, SourceType.SLOT, 10L, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void contributions_underTheCap_isNotTruncated() {

        givenVisibleSignals(signal(1L, "alice", "kafka", SourceType.SLOT, 10L));

        assertThat(sut.contributions(SLUG).truncated()).isFalse();
    }

    @Test
    void contributions_atTheCap_isNotTruncated() {

        givenVisibleSignals(manySignals(ExpertiseMapService.MAP_CONTRIBUTION_LIMIT));

        var result = sut.contributions(SLUG);

        assertThat(result.items()).hasSize(ExpertiseMapService.MAP_CONTRIBUTION_LIMIT);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void contributions_pastTheCap_reportsTruncatedAndDropsTheExtraRow() {

        givenVisibleSignals(manySignals(ExpertiseMapService.MAP_CONTRIBUTION_LIMIT + 1));

        var result = sut.contributions(SLUG);

        assertThat(result.items()).hasSize(ExpertiseMapService.MAP_CONTRIBUTION_LIMIT);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void contributions_asksTheRepositoryForOneMoreThanTheCap() {

        sut.contributions(SLUG);

        verify(expertiseQueryRepository).visibleSignals("org-1", 1L, ExpertiseMapService.MAP_CONTRIBUTION_LIMIT + 1);
    }

    @Test
    void contributions_personTheDirectoryCannotName_isADeletedMember() {

        givenVisibleSignals(signal(1L, "ghost", "kafka", SourceType.SLOT, 10L));
        givenDirectory(Map.of());

        assertThat(sut.contributions(SLUG).people()).containsExactly(new MapPerson("ghost", "Deleted member", null));
    }

    @Test
    void contributions_noSignals_isEmptyAndNotTruncated() {

        givenVisibleSignals();

        var result = sut.contributions(SLUG);

        assertThat(result.items()).isEmpty();
        assertThat(result.people()).isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    // -------------------------------------------------------------------------
    // topicMap()
    // -------------------------------------------------------------------------

    /** The map draws an area nobody has practised, so it reads declarations on purpose. */
    @Test
    void theMapReadsDeclarationsAndDrawsAnAreaNobodyHasPractised() {

        // given
        givenAggregates(aggregate("ana", "kafka"));
        givenDeclarationsInCommunity(
                Declaration.builder().communityId(1L).userId("bruno").tag("graphql").declaredAt(EARLY).build());

        // when
        var map = sut.topicMap(SLUG);

        // then
        assertThat(map.topics()).extracting(TopicNode::canonicalTag).containsExactly("graphql", "kafka");
        assertThat(map.topics()).filteredOn(t -> t.canonicalTag().equals("graphql")).singleElement().satisfies(t -> {
            assertThat(t.openToQuestionsCount()).isEqualTo(1);
            assertThat(t.practitionerCount()).isZero();
            assertThat(t.lastActivityAt()).isNull();
        });
    }

    @Test
    void topicMap_resolvesDisplayNamesWithOnePersonDirectoryCallForEveryone() {

        // given - ana is known to the directory, bruno is not
        var rows = List.of(new UserTagAggregate("ana", "kafka", 3, 0, 0, Instant.EPOCH),
                new UserTagAggregate("bruno", "ddd", 2, 0, 0, Instant.EPOCH));
        when(expertiseQueryRepository.userTagAggregates("org-1", 1L)).thenReturn(rows);
        when(declarationRepository.allVisibleIn("org-1", 1L)).thenReturn(List.of());
        when(personDirectory.resolve(any())).thenReturn(Map.of("ana", new Person("ana", "Ana Silva", null, null)));

        // when
        var map = sut.topicMap(SLUG);

        // then - the resolved user gets their display name, the unresolved one is a deleted member, never their id
        assertThat(map.topics()).extracting(TopicNode::canonicalTag, TopicNode::topHolderName)
                .containsExactlyInAnyOrder(tuple("kafka", "Ana Silva"), tuple("ddd", "Deleted member"));
        verify(personDirectory, times(1)).resolve(Set.of("ana", "bruno"));
    }

    @Test
    void topicMap_carriesTheCommunitysNamedAreasFoldedOntoTheCanonicalsItDraws() {

        // given - the tag was placed as a variant, then merged into kafka
        givenAggregates(aggregate("ana", "kafka"));
        givenAliases(Map.of("apache-kafka", "kafka"));
        when(namedAreaRepository.findAll("org-1", 1L))
                .thenReturn(List.of(new NamedArea(7L, "Streaming", List.of("apache-kafka"))));

        // when
        var map = sut.topicMap(SLUG);

        // then
        assertThat(map.namedAreas()).containsExactly(new NamedArea(7L, "Streaming", List.of("kafka")));
    }

    @Test
    void topicMap_forACommunityWithNoNamedAreas_carriesAnEmptyList() {

        // given
        givenAggregates(aggregate("ana", "kafka"));

        // when
        var map = sut.topicMap(SLUG);

        // then
        assertThat(map.namedAreas()).isNotNull().isEmpty();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static ExpertiseSignal signal(long id, String userId, String tag, SourceType sourceType, long sourceId) {

        return ExpertiseSignalMother.any().id(id).userId(userId).tag(tag).sourceType(sourceType).sourceId(sourceId)
                .build();
    }

    private static ExpertiseSignal[] manySignals(int count) {

        var signals = new ExpertiseSignal[count];
        for (var i = 0; i < count; i++) {
            signals[i] = signal(i + 1L, "alice", "kafka", SourceType.SLOT, i + 1L);
        }
        return signals;
    }

    private void givenVisibleSignals(ExpertiseSignal... signals) {

        when(expertiseQueryRepository.visibleSignals(eq("org-1"), eq(1L), anyInt())).thenReturn(List.of(signals));
    }

    private void givenAliases(Map<String, String> aliasMap) {

        when(tagAliasRepository.aliasMap("org-1", 1L)).thenReturn(aliasMap);
    }

    private void givenReach(Map<SourceRef, Integer> reach) {

        when(sourceReachResolver.reachFor(any())).thenReturn(reach);
    }

    private void givenDirectory(Map<String, Person> people) {

        when(personDirectory.resolve(any())).thenReturn(people);
    }

    private static UserTagAggregate aggregate(String userId, String tag) {

        return new UserTagAggregate(userId, tag, 1, 0, 0, EARLY);
    }

    private void givenAggregates(UserTagAggregate... rows) {

        when(expertiseQueryRepository.userTagAggregates("org-1", 1L)).thenReturn(List.of(rows));
    }

    private void givenDeclarationsInCommunity(Declaration... declarations) {

        when(declarationRepository.allVisibleIn("org-1", 1L)).thenReturn(List.of(declarations));
    }
}
