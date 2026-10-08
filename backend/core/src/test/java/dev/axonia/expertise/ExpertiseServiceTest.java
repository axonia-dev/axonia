package dev.axonia.expertise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.ForbiddenException;
import dev.axonia.expertise.internal.DeclarationRepository;
import dev.axonia.expertise.internal.EvidenceEnricher;
import dev.axonia.expertise.internal.ExpertiseQueryRepository;
import dev.axonia.expertise.internal.TagAliasRepository;
import dev.axonia.expertise.mother.ExpertiseSignalMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class ExpertiseServiceTest {

    private static final Instant EARLY = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant LATE = Instant.parse("2026-09-12T12:00:00Z");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private ExpertiseQueryRepository expertiseQueryRepository;
    @Mock
    private TagAliasRepository tagAliasRepository;
    @Mock
    private EvidenceEnricher evidenceEnricher;
    @Mock
    private DeclarationRepository declarationRepository;

    private ExpertiseService sut;

    @BeforeEach
    void setUp() {

        sut = new ExpertiseService(organizationContext, currentUser, communityAccess, personDirectory,
                expertiseQueryRepository, tagAliasRepository, evidenceEnricher, declarationRepository);
        when(declarationRepository.visibleIn(any(), anyLong(), any())).thenReturn(List.of());
        when(organizationContext.organizationId()).thenReturn("org-1");
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(tagAliasRepository.aliasMap("org-1", 1L)).thenReturn(Map.of());
        when(personDirectory.resolve(any())).thenReturn(Map.of());
        when(evidenceEnricher.enrich(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // whoKnows - blank term
    // -------------------------------------------------------------------------

    @Test
    void whoKnows_blankTerm_returnsEmpty() {

        assertThat(sut.whoKnows("eng", "  ", false)).isEmpty();
        assertThat(sut.whoKnows("eng", null, false)).isEmpty();
        assertThat(sut.whoKnows("eng", "", false)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // whoKnows - declarations
    // -------------------------------------------------------------------------

    @Test
    void whoKnows_declaredOnlyMember_isOpenToQuestionsByDefault() {

        // given
        when(declarationRepository.visibleIn(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(declared("dana", "kafka", LATE)));
        when(personDirectory.resolve(any())).thenReturn(Map.of("dana", new Person("dana", "Dana", null, "SRE")));

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then
        assertThat(result).singleElement().satisfies(p -> {
            assertThat(p.userId()).isEqualTo("dana");
            assertThat(p.displayName()).isEqualTo("Dana");
            assertThat(p.orgUnit()).isEqualTo("SRE");
            assertThat(p.tier()).isEqualTo(Tier.OPEN_TO_QUESTIONS);
            assertThat(p.openToQuestions()).isTrue();
            assertThat(p.declaredAt()).isEqualTo(LATE);
            assertThat(p.evidence().recent()).isEmpty();
        });
    }

    @Test
    void whoKnows_memberWhoDeclaredTwoSpellingsOfOneArea_isOnePractitionerDatedByTheEarlier() {

        // given - "k8s" is merged into "kubernetes", and dana declared both
        when(tagAliasRepository.aliasMap("org-1", 1L)).thenReturn(Map.of("k8s", "kubernetes"));
        when(declarationRepository.visibleIn("org-1", 1L, Set.of("kubernetes", "k8s")))
                .thenReturn(List.of(declared("dana", "kubernetes", LATE), declared("dana", "k8s", EARLY)));

        // when
        var result = sut.whoKnows("eng", "k8s", false);

        // then
        assertThat(result).singleElement().satisfies(p -> {
            assertThat(p.userId()).isEqualTo("dana");
            assertThat(p.tier()).isEqualTo(Tier.OPEN_TO_QUESTIONS);
            assertThat(p.declaredAt()).isEqualTo(EARLY);
        });
    }

    @Test
    void whoKnows_declaredOnlyMember_isLeftOutWhenDemonstratedOnly() {

        // given
        when(declarationRepository.visibleIn(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(declared("dana", "kafka", LATE)));

        // when / then
        assertThat(sut.whoKnows("eng", "kafka", true)).isEmpty();
    }

    @Test
    void whoKnows_evidencedMemberWithDeclarations_keepsTheEvidenceTierAndTheEarliestDate() {

        // given
        var signal = ExpertiseSignalMother.any().userId("alice").signalType(SignalType.PRESENTED).tag("kafka")
                .occurredAt(Instant.parse("2026-03-01T00:00:00Z")).build();
        when(expertiseQueryRepository.findPractitioners(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals("alice", List.of(signal))));
        when(declarationRepository.visibleIn(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(declared("alice", "kafka", LATE), declared("alice", "apache-kafka", EARLY)));

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then
        assertThat(result).singleElement().satisfies(p -> {
            assertThat(p.tier()).isEqualTo(Tier.PRACTITIONER);
            assertThat(p.openToQuestions()).isTrue();
            assertThat(p.declaredAt()).isEqualTo(EARLY);
        });
    }

    @Test
    void whoKnows_evidencedMemberWithDeclaration_appearsOnce() {

        // given
        var signal = ExpertiseSignalMother.any().userId("alice").signalType(SignalType.PRESENTED).tag("kafka").build();
        when(expertiseQueryRepository.findPractitioners(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals("alice", List.of(signal))));
        when(declarationRepository.visibleIn(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(declared("alice", "kafka", LATE)));

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then
        assertThat(result).singleElement().satisfies(p -> {
            assertThat(p.tier()).isEqualTo(Tier.PRACTITIONER);
            assertThat(p.declaredAt()).isEqualTo(LATE);
        });
    }

    @Test
    void whoKnows_evidencedMemberWithoutDeclaration_carriesNoMarker() {

        // given
        var signal = ExpertiseSignalMother.any().userId("alice").signalType(SignalType.PRESENTED).tag("kafka").build();
        when(expertiseQueryRepository.findPractitioners(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals("alice", List.of(signal))));

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then
        assertThat(result).singleElement().satisfies(p -> {
            assertThat(p.openToQuestions()).isFalse();
            assertThat(p.declaredAt()).isNull();
        });
    }

    @Test
    void whoKnows_openToQuestionsRanksBelowInterested() {

        // given
        var interested = ExpertiseSignalMother.any().userId("carol").signalType(SignalType.OFFERED).tag("kafka")
                .build();
        when(expertiseQueryRepository.findPractitioners(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals("carol", List.of(interested))));
        when(declarationRepository.visibleIn(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(declared("dana", "kafka", LATE)));

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then
        assertThat(result).extracting(Practitioner::userId).containsExactly("carol", "dana");
    }

    @Test
    void whoKnows_declaredMemberUnknownToTheDirectory_isADeletedMember() {

        // given
        when(declarationRepository.visibleIn(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(declared("ghost", "kafka", LATE)));

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then
        assertThat(result).singleElement().extracting(Practitioner::displayName).isEqualTo("Deleted member");
    }

    /** A suggestion puts a person in front of a room, which willingness alone does not earn. */
    @Test
    void suggestSpeakersNeverReadsDeclarations() {

        // given
        when(communityAccess.isOrganizer(1L, "leader")).thenReturn(true);
        when(currentUser.userId()).thenReturn("leader");

        // when
        sut.suggestSpeakers("eng", List.of("kafka"));

        // then
        verifyNoInteractions(declarationRepository);
    }

    // -------------------------------------------------------------------------
    // whoKnows - recency tiebreak within same tier
    // -------------------------------------------------------------------------

    @Test
    void whoKnows_recencyTiebreak_newerFirst() {

        // given
        var olderSignal = ExpertiseSignalMother.any().userId("alice").signalType(SignalType.PRESENTED)
                .occurredAt(Instant.parse("2025-01-01T00:00:00Z")).tag("kafka").build();
        var newerSignal = ExpertiseSignalMother.any().userId("bob").signalType(SignalType.PRESENTED)
                .occurredAt(Instant.parse("2026-06-01T00:00:00Z")).tag("kafka").build();

        when(expertiseQueryRepository.findPractitioners(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals("alice", List.of(olderSignal)),
                        new ExpertiseQueryRepository.UserSignals("bob", List.of(newerSignal))));
        when(personDirectory.resolve(any())).thenReturn(
                Map.of("alice", new Person("alice", "Alice", null, null), "bob", new Person("bob", "Bob", null, null)));

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then - both PRACTITIONER, bob is newer so comes first
        assertThat(result).extracting(Practitioner::userId).containsExactly("bob", "alice");
    }

    // -------------------------------------------------------------------------
    // whoKnows - the demonstratedOnly filter
    // -------------------------------------------------------------------------

    @Test
    void whoKnows_byDefault_listsEveryTierDownToADeclaration() {

        // given
        givenTheFourClaims();

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then
        assertThat(result).extracting(Practitioner::userId).containsExactly("alice", "bob", "carol", "dana");
        assertThat(result).extracting(Practitioner::tier).containsExactly(Tier.PRACTITIONER, Tier.CONTRIBUTOR,
                Tier.INTERESTED, Tier.OPEN_TO_QUESTIONS);
    }

    @Test
    void whoKnows_demonstratedOnly_keepsOnlyPractitionersAndContributors() {

        // given
        givenTheFourClaims();

        // when
        var result = sut.whoKnows("eng", "kafka", true);

        // then - the offer and the declaration are both dropped
        assertThat(result).extracting(Practitioner::userId).containsExactly("alice", "bob");
    }

    private void givenTheFourClaims() {

        var presented = ExpertiseSignalMother.any().userId("alice").signalType(SignalType.PRESENTED).tag("kafka")
                .build();
        var contributed = ExpertiseSignalMother.any().userId("bob").signalType(SignalType.CONTRIBUTED).tag("kafka")
                .build();
        var offered = ExpertiseSignalMother.any().userId("carol").signalType(SignalType.OFFERED).tag("kafka").build();
        when(expertiseQueryRepository.findPractitioners(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals("carol", List.of(offered)),
                        new ExpertiseQueryRepository.UserSignals("bob", List.of(contributed)),
                        new ExpertiseQueryRepository.UserSignals("alice", List.of(presented))));
        when(declarationRepository.visibleIn(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(declared("dana", "kafka", LATE)));
    }

    // -------------------------------------------------------------------------
    // whoKnows - order within the open-to-questions tier
    // -------------------------------------------------------------------------

    @Test
    void whoKnows_openToQuestions_ordersByTheDateDeclaredNewestFirst() {

        // given
        when(declarationRepository.visibleIn(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(declared("erin", "kafka", EARLY), declared("dana", "kafka", LATE)));
        when(personDirectory.resolve(any())).thenReturn(
                Map.of("dana", new Person("dana", "Dana", null, null), "erin", new Person("erin", "Erin", null, null)));

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then
        assertThat(result).extracting(Practitioner::userId).containsExactly("dana", "erin");
    }

    @Test
    void whoKnows_openToQuestionsDeclaredOnTheSameDay_ordersByDisplayName() {

        // given
        when(declarationRepository.visibleIn(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(declared("dana", "kafka", LATE), declared("erin", "kafka", LATE)));
        when(personDirectory.resolve(any())).thenReturn(
                Map.of("dana", new Person("dana", "Zoe", null, null), "erin", new Person("erin", "adam", null, null)));

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then - case is not what orders people, so "adam" still comes before "Zoe"
        assertThat(result).extracting(Practitioner::displayName).containsExactly("adam", "Zoe");
    }

    // -------------------------------------------------------------------------
    // whoKnows - an unresolved person is a deleted member
    // -------------------------------------------------------------------------

    @Test
    void whoKnows_unresolvedPerson_isADeletedMember() {

        // given
        var signal = ExpertiseSignalMother.any().userId("unknown-user").signalType(SignalType.PRESENTED).tag("kafka")
                .build();
        when(expertiseQueryRepository.findPractitioners(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals("unknown-user", List.of(signal))));
        when(personDirectory.resolve(any())).thenReturn(Map.of()); // unresolved

        // when
        var result = sut.whoKnows("eng", "kafka", false);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo("unknown-user");
        assertThat(result.get(0).displayName()).isEqualTo("Deleted member");
        assertThat(result.get(0).avatarUrl()).isNull();
        assertThat(result.get(0).orgUnit()).isNull();
    }

    // -------------------------------------------------------------------------
    // whoKnows - alias expansion
    // -------------------------------------------------------------------------

    @Test
    void whoKnows_aliasExpansion_pullsVariantTaggedSignals() {

        // given - "k8s" is a variant of "kubernetes"; signal is tagged "k8s"
        when(tagAliasRepository.aliasMap("org-1", 1L)).thenReturn(Map.of("k8s", "kubernetes"));

        var signalTaggedK8s = ExpertiseSignalMother.any().userId("alice").signalType(SignalType.PRESENTED).tag("k8s")
                .build();
        // finder is called with the expanded set {kubernetes, k8s}
        when(expertiseQueryRepository.findPractitioners(eq("org-1"), eq(1L), eq(Set.of("kubernetes", "k8s"))))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals("alice", List.of(signalTaggedK8s))));
        when(personDirectory.resolve(any())).thenReturn(Map.of("alice", new Person("alice", "Alice", null, null)));

        // when - search for "kubernetes" (canonical)
        var result = sut.whoKnows("eng", "kubernetes", false);

        // then - alice is found via the k8s variant
        assertThat(result).extracting(Practitioner::userId).containsExactly("alice");
    }

    // -------------------------------------------------------------------------
    // suggestSpeakers - non-organizer guard
    // -------------------------------------------------------------------------

    @Test
    void suggestSpeakers_nonOrganizer_throwsForbidden() {

        // given
        when(currentUser.userId()).thenReturn("bob");
        when(communityAccess.isOrganizer(1L, "bob")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.suggestSpeakers("eng", List.of("kafka"))).isInstanceOf(ForbiddenException.class);
    }

    // -------------------------------------------------------------------------
    // suggestSpeakers - organizer gets results capped at 8, Interested excluded
    // -------------------------------------------------------------------------

    @Test
    void suggestSpeakers_organizer_capsAtEightAndExcludesInterested() {

        // given
        when(currentUser.userId()).thenReturn("organizer");
        when(communityAccess.isOrganizer(1L, "organizer")).thenReturn(true);

        // 10 practitioners + 1 interested
        var userSignals = new java.util.ArrayList<ExpertiseQueryRepository.UserSignals>();
        var people = new java.util.HashMap<String, Person>();
        for (int i = 1; i <= 10; i++) {
            var uid = "user-" + i;
            userSignals.add(new ExpertiseQueryRepository.UserSignals(uid,
                    List.of(ExpertiseSignalMother.any().userId(uid).signalType(SignalType.PRESENTED).tag("kafka")
                            .occurredAt(Instant.ofEpochSecond(i)).build())));
            people.put(uid, new Person(uid, "User " + i, null, null));
        }
        // add one interested user
        var interested = "interested-user";
        userSignals.add(new ExpertiseQueryRepository.UserSignals(interested, List.of(
                ExpertiseSignalMother.any().userId(interested).signalType(SignalType.OFFERED).tag("kafka").build())));
        people.put(interested, new Person(interested, "Interested", null, null));

        when(expertiseQueryRepository.findPractitioners(eq("org-1"), eq(1L), any())).thenReturn(userSignals);
        when(personDirectory.resolve(any())).thenReturn(people);

        // when
        var result = sut.suggestSpeakers("eng", List.of("kafka"));

        // then - capped at 8, no INTERESTED
        assertThat(result).hasSize(8);
        assertThat(result).noneMatch(p -> p.tier() == Tier.INTERESTED);
    }

    // -------------------------------------------------------------------------
    // areas() - no tags returns empty
    // -------------------------------------------------------------------------

    @Test
    void areas_noSignalsAndNoDeclarations_returnsEmpty() {

        assertThat(sut.areas("eng", null)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // areas() - groups by canonical via alias map
    // -------------------------------------------------------------------------

    @Test
    void areas_groupsByCanonical_viaAliasMap() {

        // given - "apache-kafka" is a variant of "kafka"
        when(tagAliasRepository.aliasMap("org-1", 1L)).thenReturn(Map.of("apache-kafka", "kafka"));
        givenAggregates(aggregate("alice", "kafka"), aggregate("bob", "apache-kafka"));

        // when
        var areas = sut.areas("eng", null);

        // then - both tags collapse into one canonical area "kafka"
        assertThat(areas).hasSize(1);
        var area = areas.get(0);
        assertThat(area.canonicalTag()).isEqualTo("kafka");
        assertThat(area.variants()).containsExactlyInAnyOrder("kafka", "apache-kafka");
    }

    // -------------------------------------------------------------------------
    // areas() - search filter matches canonical
    // -------------------------------------------------------------------------

    @Test
    void areas_searchFilter_matchesIncludedArea() {

        // given
        givenAggregates(aggregate("alice", "kafka"), aggregate("alice", "spring"));

        // when - search "kaf" should match "kafka" but not "spring"
        var areas = sut.areas("eng", "kaf");

        // then
        assertThat(areas).extracting(ExpertiseArea::canonicalTag).containsExactly("kafka");
    }

    // -------------------------------------------------------------------------
    // areas() - search filter excludes non-matching
    // -------------------------------------------------------------------------

    @Test
    void areas_searchFilter_excludesNonMatchingArea() {

        // given
        givenAggregates(aggregate("alice", "kafka"), aggregate("alice", "spring"));

        // when
        var areas = sut.areas("eng", "spring");

        // then
        assertThat(areas).extracting(ExpertiseArea::canonicalTag).containsExactly("spring");
        assertThat(areas).noneMatch(a -> a.canonicalTag().equals("kafka"));
    }

    @Test
    void areas_searchFilter_alsoNarrowsADeclaredOnlyArea() {

        // given
        givenDeclarationsInCommunity(declared("dana", "kafka", LATE), declared("dana", "spring", LATE));

        // when
        var areas = sut.areas("eng", "kaf");

        // then
        assertThat(areas).extracting(ExpertiseArea::canonicalTag).containsExactly("kafka");
    }

    // -------------------------------------------------------------------------
    // areas() - null search includes all
    // -------------------------------------------------------------------------

    @Test
    void areas_nullSearch_includesAllAreas() {

        // given
        givenAggregates(aggregate("alice", "kafka"), aggregate("alice", "spring"));

        // when
        var areas = sut.areas("eng", null);

        // then
        assertThat(areas).extracting(ExpertiseArea::canonicalTag).containsExactlyInAnyOrder("kafka", "spring");
    }

    // -------------------------------------------------------------------------
    // areas() - blank search includes all
    // -------------------------------------------------------------------------

    @Test
    void areas_blankSearch_includesAllAreas() {

        // given
        givenAggregates(aggregate("alice", "kafka"));

        // when
        var areas = sut.areas("eng", "  ");

        // then
        assertThat(areas).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // areas() - people count
    // -------------------------------------------------------------------------

    @Test
    void areas_countsDistinctPeopleAcrossTheAreasSpellings() {

        // given - alice carries two spellings of one area, bob one
        when(tagAliasRepository.aliasMap("org-1", 1L)).thenReturn(Map.of("apache-kafka", "kafka"));
        givenAggregates(aggregate("alice", "kafka"), aggregate("alice", "apache-kafka"), aggregate("bob", "kafka"));

        // when
        var areas = sut.areas("eng", null);

        // then
        assertThat(areas).hasSize(1);
        assertThat(areas.get(0).peopleCount()).isEqualTo(2);
    }

    @Test
    void areas_anAreaOnlyDeclared_isListedAndCounted() {

        // given - nobody signalled "helm"; dana declared it
        givenDeclarationsInCommunity(declared("dana", "helm", LATE));

        // when
        var areas = sut.areas("eng", null);

        // then
        assertThat(areas).singleElement().satisfies(area -> {
            assertThat(area.canonicalTag()).isEqualTo("helm");
            assertThat(area.variants()).containsExactly("helm");
            assertThat(area.peopleCount()).isEqualTo(1);
        });
    }

    @Test
    void areas_aPersonWithASignalAndADeclarationOnTheSameArea_isCountedOnce() {

        // given
        givenAggregates(aggregate("alice", "kafka"));
        givenDeclarationsInCommunity(declared("alice", "kafka", LATE), declared("dana", "kafka", EARLY));

        // when
        var areas = sut.areas("eng", null);

        // then
        assertThat(areas).singleElement().extracting(ExpertiseArea::peopleCount).isEqualTo(2);
    }

    @Test
    void areas_aDeclarationUnderAVariant_landsOnTheCanonicalArea() {

        // given - "k8s" is merged into "kubernetes", which only a declaration carries
        when(tagAliasRepository.aliasMap("org-1", 1L)).thenReturn(Map.of("k8s", "kubernetes"));
        givenDeclarationsInCommunity(declared("dana", "k8s", LATE));

        // when
        var areas = sut.areas("eng", null);

        // then
        assertThat(areas).singleElement().satisfies(area -> {
            assertThat(area.canonicalTag()).isEqualTo("kubernetes");
            assertThat(area.variants()).containsExactly("k8s");
            assertThat(area.peopleCount()).isEqualTo(1);
        });
    }

    @Test
    void areas_neverReadTheUnfilteredTagList() {

        // given
        givenAggregates(aggregate("alice", "kafka"));

        // when
        sut.areas("eng", null);

        // then - allTags carries no privacy filter, so an area is only ever built from the filtered reads
        verify(expertiseQueryRepository, never()).allTags(any(), any());
    }

    @Test
    void areas_peopleCount_isTheNumberOfPeopleTheUnfilteredSearchLists() {

        // given - alice signalled and declared, carol only offered, dana only declared
        var presented = ExpertiseSignalMother.any().userId("alice").signalType(SignalType.PRESENTED).tag("kafka")
                .build();
        var offered = ExpertiseSignalMother.any().userId("carol").signalType(SignalType.OFFERED).tag("kafka").build();
        var declarations = List.of(declared("alice", "kafka", LATE), declared("dana", "kafka", EARLY));
        givenAggregates(aggregate("alice", "kafka"), aggregate("carol", "kafka"));
        when(declarationRepository.allVisibleIn("org-1", 1L)).thenReturn(declarations);
        when(expertiseQueryRepository.findPractitioners(eq("org-1"), eq(1L), any()))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals("alice", List.of(presented)),
                        new ExpertiseQueryRepository.UserSignals("carol", List.of(offered))));
        when(declarationRepository.visibleIn(eq("org-1"), eq(1L), any())).thenReturn(declarations);

        // when
        var area = sut.areas("eng", null).get(0);

        // then - the size on the card is the length of the list the card opens
        assertThat(area.peopleCount()).isEqualTo(sut.whoKnows("eng", area.canonicalTag(), false).size()).isEqualTo(3);
    }

    private static Declaration declared(String userId, String tag, Instant at) {

        return Declaration.builder().communityId(1L).userId(userId).tag(tag).declaredAt(at).build();
    }

    private static ExpertiseQueryRepository.UserTagAggregate aggregate(String userId, String tag) {

        return new ExpertiseQueryRepository.UserTagAggregate(userId, tag, 1, 0, 0, EARLY);
    }

    private void givenAggregates(ExpertiseQueryRepository.UserTagAggregate... rows) {

        when(expertiseQueryRepository.userTagAggregates("org-1", 1L)).thenReturn(List.of(rows));
    }

    private void givenDeclarationsInCommunity(Declaration... declarations) {

        when(declarationRepository.allVisibleIn("org-1", 1L)).thenReturn(List.of(declarations));
    }
}
