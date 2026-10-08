package dev.axonia.expertise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.expertise.fake.FakeTagAliasRepository;
import dev.axonia.expertise.internal.DeclarationRepository;
import dev.axonia.expertise.internal.EvidenceEnricher;
import dev.axonia.expertise.internal.ExpertiseQueryRepository;
import dev.axonia.expertise.internal.PreferenceRepository;
import dev.axonia.expertise.internal.PreferenceRepository.SignalRef;
import dev.axonia.expertise.internal.TierPolicy;
import dev.axonia.expertise.mother.ExpertiseSignalMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class ExpertisePreferenceServiceTest {

    private static final String ORG = "org-1";
    private static final String USER = "alice";
    private static final long COMMUNITY_ONE = 1L;
    private static final long COMMUNITY_TWO = 2L;
    private static final Instant DECLARED = Instant.parse("2026-09-12T12:00:00Z");
    private static final Instant LATER = Instant.parse("2026-09-14T12:00:00Z");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private ExpertiseQueryRepository expertiseQueryRepository;
    @Mock
    private PreferenceRepository preferenceRepository;
    @Mock
    private EvidenceEnricher evidenceEnricher;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private DeclarationRepository declarationRepository;

    private FakeTagAliasRepository tagAliasRepository;

    private ExpertisePreferenceService sut;

    @BeforeEach
    void setUp() {

        tagAliasRepository = new FakeTagAliasRepository();
        sut = new ExpertisePreferenceService(organizationContext, currentUser, expertiseQueryRepository,
                tagAliasRepository, preferenceRepository, evidenceEnricher, communityAccess, declarationRepository);
        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(USER);
        when(preferenceRepository.hiddenTags(ORG, USER)).thenReturn(Set.of());
        when(preferenceRepository.isOptedOut(ORG, USER)).thenReturn(false);
        when(evidenceEnricher.enrich(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(evidenceEnricher.enrichItems(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(communityAccess.summaryById(any())).thenReturn(Map.of());
        when(declarationRepository.forUser(ORG, USER)).thenReturn(List.of());
    }

    // ------------------------------------------------------------------
    // selfView - basic area building
    // ------------------------------------------------------------------

    @Test
    void selfView_noSignals_returnsEmptyAreas() {

        when(expertiseQueryRepository.findForUser(ORG, USER)).thenReturn(List.of());

        var result = sut.selfView();

        assertThat(result.optedOut()).isFalse();
        assertThat(result.areas()).isEmpty();
    }

    @Test
    void selfView_signalsGroupedByCanonicalTag() {

        var s1 = ExpertiseSignalMother.any().userId(USER).tag("kafka").signalType(SignalType.PRESENTED).build();
        var s2 = ExpertiseSignalMother.any().userId(USER).tag("kafka").signalType(SignalType.CONTRIBUTED).build();
        var s3 = ExpertiseSignalMother.any().userId(USER).tag("java").signalType(SignalType.OFFERED).build();

        givenSignals(s1, s2, s3);

        var result = sut.selfView();

        assertThat(result.areas()).hasSize(2);
        var kafkaArea = result.areas().stream().filter(a -> a.canonicalTag().equals("kafka")).findFirst().orElseThrow();
        assertThat(kafkaArea.tier()).isEqualTo(Tier.PRACTITIONER);
        assertThat(kafkaArea.evidence().presentedCount()).isEqualTo(1);
        assertThat(kafkaArea.evidence().contributedCount()).isEqualTo(1);

        var javaArea = result.areas().stream().filter(a -> a.canonicalTag().equals("java")).findFirst().orElseThrow();
        assertThat(javaArea.tier()).isEqualTo(Tier.INTERESTED);
    }

    @Test
    void selfView_aliasVariantsMergedIntoCanonical() {

        // "k8s" is a variant of "kubernetes" in the community both signals came from
        tagAliasRepository.addAlias(ORG, COMMUNITY_ONE, "k8s", "kubernetes");

        var k8sSignal = ExpertiseSignalMother.any().userId(USER).communityId(COMMUNITY_ONE).tag("k8s")
                .signalType(SignalType.PRESENTED).build();
        var kubeSignal = ExpertiseSignalMother.any().userId(USER).communityId(COMMUNITY_ONE).tag("kubernetes")
                .signalType(SignalType.CONTRIBUTED).build();

        givenSignals(k8sSignal, kubeSignal);

        var result = sut.selfView();

        // Both signals collapse under the canonical tag "kubernetes"
        assertThat(result.areas()).hasSize(1);
        assertThat(result.areas().get(0).canonicalTag()).isEqualTo("kubernetes");
        assertThat(result.areas().get(0).tier()).isEqualTo(Tier.PRACTITIONER);
    }

    @Test
    void aTagResolvesThroughTheVocabularyOfTheCommunityItWasSignalledIn() {

        // The same raw tag, signalled in two communities that curate it differently.
        var inOne = ExpertiseSignalMother.any().userId(USER).communityId(COMMUNITY_ONE).tag("kafka").build();
        var inTwo = ExpertiseSignalMother.any().userId(USER).communityId(COMMUNITY_TWO).tag("kafka").build();
        givenSignals(inOne, inTwo);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ONE, "kafka", "apache-kafka");

        var view = sut.selfView();

        assertThat(view.areas()).extracting(SelfArea::canonicalTag).containsExactlyInAnyOrder("apache-kafka", "kafka");
    }

    @Test
    void selfView_hiddenFlagReflectedCorrectly() {

        when(preferenceRepository.hiddenTags(ORG, USER)).thenReturn(Set.of("kafka"));

        var signal = ExpertiseSignalMother.any().userId(USER).tag("kafka").signalType(SignalType.PRESENTED).build();
        var signal2 = ExpertiseSignalMother.any().userId(USER).tag("java").signalType(SignalType.PRESENTED).build();

        givenSignals(signal, signal2);

        var result = sut.selfView();

        var kafka = result.areas().stream().filter(a -> a.canonicalTag().equals("kafka")).findFirst().orElseThrow();
        assertThat(kafka.hidden()).isTrue();

        var java = result.areas().stream().filter(a -> a.canonicalTag().equals("java")).findFirst().orElseThrow();
        assertThat(java.hidden()).isFalse();
    }

    @Test
    void selfView_optedOutFlagReflectedCorrectly() {

        when(preferenceRepository.isOptedOut(ORG, USER)).thenReturn(true);
        when(expertiseQueryRepository.findForUser(ORG, USER)).thenReturn(List.of());

        var result = sut.selfView();

        assertThat(result.optedOut()).isTrue();
    }

    @Test
    void selfView_areasSortedByTierRankThenAlphabetically() {

        var practitioner = ExpertiseSignalMother.any().userId(USER).tag("kafka").signalType(SignalType.PRESENTED)
                .build();
        var contributor = ExpertiseSignalMother.any().userId(USER).tag("spring-boot").signalType(SignalType.CONTRIBUTED)
                .build();
        var interested = ExpertiseSignalMother.any().userId(USER).tag("aws").signalType(SignalType.OFFERED).build();
        var practitioner2 = ExpertiseSignalMother.any().userId(USER).tag("java").signalType(SignalType.PRESENTED)
                .build();

        givenSignals(interested, contributor, practitioner2, practitioner);

        var result = sut.selfView();

        // Practitioners first (alphabetically), then contributor, then interested
        assertThat(result.areas()).extracting(SelfArea::canonicalTag).containsExactly("java", "kafka", "spring-boot",
                "aws");
        assertThat(result.areas()).extracting(SelfArea::tier).containsExactly(Tier.PRACTITIONER, Tier.PRACTITIONER,
                Tier.CONTRIBUTOR, Tier.INTERESTED);
    }

    @Test
    void selfView_areaCarriesItsDistinctCommunities() {

        when(communityAccess.summaryById(any())).thenReturn(
                Map.of(10L, new CommunitySummary("eng", "Engineering"), 20L, new CommunitySummary("plat", "Platform")));

        var s1 = ExpertiseSignalMother.any().userId(USER).tag("kafka").communityId(10L).signalType(SignalType.PRESENTED)
                .build();
        var s2 = ExpertiseSignalMother.any().userId(USER).tag("kafka").communityId(20L)
                .signalType(SignalType.CONTRIBUTED).build();

        givenSignals(s1, s2);

        var view = sut.selfView();
        var kafka = view.areas().stream().filter(a -> a.canonicalTag().equals("kafka")).findFirst().orElseThrow();
        assertThat(kafka.communities()).extracting(AreaCommunity::name).containsExactlyInAnyOrder("Engineering",
                "Platform");
        assertThat(kafka.communities()).extracting(AreaCommunity::slug).containsExactlyInAnyOrder("eng", "plat");
    }

    @Test
    void selfView_communityNoLongerResolves_omittedFromArea() {

        when(communityAccess.summaryById(any())).thenReturn(Map.of());

        var signal = ExpertiseSignalMother.any().userId(USER).tag("kafka").communityId(10L)
                .signalType(SignalType.PRESENTED).build();

        givenSignals(signal);

        var view = sut.selfView();
        var kafka = view.areas().stream().filter(a -> a.canonicalTag().equals("kafka")).findFirst().orElseThrow();
        assertThat(kafka.communities()).isEmpty();
    }

    // ------------------------------------------------------------------
    // optOut - delegates with correct scope
    // ------------------------------------------------------------------

    @Test
    void optOut_true_delegatesToRepository() {

        sut.optOut(true);

        verify(preferenceRepository).setOptOut(ORG, USER, true);
    }

    @Test
    void optOut_false_delegatesToRepository() {

        sut.optOut(false);

        verify(preferenceRepository).setOptOut(ORG, USER, false);
    }

    // ------------------------------------------------------------------
    // hideTag / unhideTag - normalize the input, resolve no alias
    // ------------------------------------------------------------------

    @Test
    void hideTag_normalizesInput() {

        sut.hideTag("Spring Boot");

        verify(preferenceRepository).hideTag(ORG, USER, "spring-boot");
    }

    @Test
    void hideTag_doesNotResolveThroughAnyCommunitysAliasMap() {

        // Hiding is an org-scoped personal preference matched against the raw signal tag, so a community's
        // merge of "k8s" into "kubernetes" must not rewrite what the user asked to hide.
        tagAliasRepository.addAlias(ORG, COMMUNITY_ONE, "k8s", "kubernetes");

        sut.hideTag("K8S");

        verify(preferenceRepository).hideTag(ORG, USER, "k8s");
    }

    @Test
    void unhideTag_normalizesInput() {

        sut.unhideTag("Spring Boot");

        verify(preferenceRepository).unhideTag(ORG, USER, "spring-boot");
    }

    @Test
    void unhideTag_doesNotResolveThroughAnyCommunitysAliasMap() {

        tagAliasRepository.addAlias(ORG, COMMUNITY_ONE, "k8s", "kubernetes");

        sut.unhideTag("K8S");

        verify(preferenceRepository).unhideTag(ORG, USER, "k8s");
    }

    // ------------------------------------------------------------------
    // dismissSignal - normalize the tag, delegate with scope
    // ------------------------------------------------------------------

    @Test
    void dismissSignal_normalizesTagAndDelegates() {

        sut.dismissSignal(SourceType.SLOT, 42L, "Spring Boot");

        verify(preferenceRepository).dismissSignal(ORG, USER, new SignalRef(SourceType.SLOT, 42L), "spring-boot");
    }

    @Test
    void dismissSignal_doesNotResolveThroughAnyCommunitysAliasMap() {

        tagAliasRepository.addAlias(ORG, COMMUNITY_ONE, "k8s", "kubernetes");

        sut.dismissSignal(SourceType.ARTIFACT, 99L, "k8s");

        verify(preferenceRepository).dismissSignal(ORG, USER, new SignalRef(SourceType.ARTIFACT, 99L), "k8s");
    }

    // ------------------------------------------------------------------
    // areaEvidence - full paged evidence for one area
    // ------------------------------------------------------------------

    @Test
    void areaEvidence_pagesNewestFirstWithHasMore() {

        var signals = IntStream.range(0, 8).mapToObj(i -> ExpertiseSignalMother.any().userId(USER).tag("kafka")
                .sourceId((long) i).occurredAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i)).build()).toList();
        when(expertiseQueryRepository.findForUser(ORG, USER))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals(USER, signals)));

        var page = sut.areaEvidence("kafka", 0, 5);
        assertThat(page.items()).hasSize(5);
        assertThat(page.hasMore()).isTrue();

        var next = sut.areaEvidence("kafka", 5, 5);
        assertThat(next.items()).hasSize(3);
        assertThat(next.hasMore()).isFalse();
    }

    @Test
    void areaEvidence_readsTheAreaThroughTheSignalsOwnCommunityVocabulary() {

        tagAliasRepository.addAlias(ORG, COMMUNITY_ONE, "k8s", "kubernetes");
        var signal = ExpertiseSignalMother.any().userId(USER).communityId(COMMUNITY_ONE).tag("k8s").build();
        givenSignals(signal);

        assertThat(sut.areaEvidence("kubernetes", 0, 20).items()).hasSize(1);
    }

    @Test
    void areaEvidence_unknownTagIsEmpty() {

        when(expertiseQueryRepository.findForUser(ORG, USER)).thenReturn(List.of());

        assertThat(sut.areaEvidence("nope", 0, 20).items()).isEmpty();
    }

    // ------------------------------------------------------------------
    // selfView - declarations
    // ------------------------------------------------------------------

    @Test
    void selfView_listsDeclarationsCanonicalizedInTheirOwnCommunity() {

        // given - "k8s" is merged into "kubernetes" in community one only
        tagAliasRepository.addAlias(ORG, COMMUNITY_ONE, "k8s", "kubernetes");
        when(expertiseQueryRepository.findForUser(ORG, USER)).thenReturn(List.of());
        when(declarationRepository.forUser(ORG, USER)).thenReturn(List.of(
                Declaration.builder().communityId(COMMUNITY_ONE).userId(USER).tag("k8s").declaredAt(DECLARED).build(),
                Declaration.builder().communityId(COMMUNITY_TWO).userId(USER).tag("k8s").declaredAt(DECLARED).build()));
        when(communityAccess.summaryById(any())).thenReturn(Map.of(COMMUNITY_ONE,
                new CommunitySummary("platform", "Platform"), COMMUNITY_TWO, new CommunitySummary("data", "Data")));

        // when
        var result = sut.selfView();

        // then - sorted by community name, each tag read through its own community's merges
        assertThat(result.declarations()).containsExactly(
                SelfDeclaration.builder().tag("k8s").communityId(COMMUNITY_TWO).communityName("Data")
                        .communitySlug("data").hidden(false).declaredAt(DECLARED).build(),
                SelfDeclaration.builder().tag("kubernetes").communityId(COMMUNITY_ONE).communityName("Platform")
                        .communitySlug("platform").hidden(false).declaredAt(DECLARED).build());
    }

    @Test
    void selfView_marksADeclarationWhoseTagIsHidden() {

        // given
        when(expertiseQueryRepository.findForUser(ORG, USER)).thenReturn(List.of());
        when(preferenceRepository.hiddenTags(ORG, USER)).thenReturn(Set.of("kafka"));
        when(declarationRepository.forUser(ORG, USER)).thenReturn(List.of(Declaration.builder()
                .communityId(COMMUNITY_ONE).userId(USER).tag("kafka").declaredAt(DECLARED).build()));
        when(communityAccess.summaryById(any()))
                .thenReturn(Map.of(COMMUNITY_ONE, new CommunitySummary("platform", "Platform")));

        // when / then
        assertThat(sut.selfView().declarations()).singleElement().extracting(SelfDeclaration::hidden).isEqualTo(true);
    }

    @Test
    void selfView_showsTwoDeclaredSpellingsOfOneAreaAsOneRowDatedByTheEarlier() {

        // given - "k8s" is merged into "kubernetes", and the member declared both
        tagAliasRepository.addAlias(ORG, COMMUNITY_ONE, "k8s", "kubernetes");
        when(expertiseQueryRepository.findForUser(ORG, USER)).thenReturn(List.of());
        when(declarationRepository.forUser(ORG, USER)).thenReturn(List.of(
                Declaration.builder().communityId(COMMUNITY_ONE).userId(USER).tag("kubernetes").declaredAt(LATER)
                        .build(),
                Declaration.builder().communityId(COMMUNITY_ONE).userId(USER).tag("k8s").declaredAt(DECLARED).build()));
        when(communityAccess.summaryById(any()))
                .thenReturn(Map.of(COMMUNITY_ONE, new CommunitySummary("platform", "Platform")));

        // when / then
        assertThat(sut.selfView().declarations()).containsExactly(
                SelfDeclaration.builder().tag("kubernetes").communityId(COMMUNITY_ONE).communityName("Platform")
                        .communitySlug("platform").hidden(false).declaredAt(DECLARED).build());
    }

    @Test
    void selfView_marksADeclaredVariantHiddenWhenItsCanonicalTagIsHidden() {

        // given
        tagAliasRepository.addAlias(ORG, COMMUNITY_ONE, "k8s", "kubernetes");
        when(expertiseQueryRepository.findForUser(ORG, USER)).thenReturn(List.of());
        when(preferenceRepository.hiddenTags(ORG, USER)).thenReturn(Set.of("kubernetes"));
        when(declarationRepository.forUser(ORG, USER)).thenReturn(List.of(
                Declaration.builder().communityId(COMMUNITY_ONE).userId(USER).tag("k8s").declaredAt(DECLARED).build()));
        when(communityAccess.summaryById(any()))
                .thenReturn(Map.of(COMMUNITY_ONE, new CommunitySummary("platform", "Platform")));

        // when / then
        assertThat(sut.selfView().declarations()).singleElement().satisfies(declaration -> {
            assertThat(declaration.tag()).isEqualTo("kubernetes");
            assertThat(declaration.hidden()).isTrue();
        });
    }

    @Test
    void selfView_sortsDeclarationsByCommunityNameIgnoringCase() {

        // given
        when(expertiseQueryRepository.findForUser(ORG, USER)).thenReturn(List.of());
        when(declarationRepository.forUser(ORG, USER)).thenReturn(List.of(
                Declaration.builder().communityId(COMMUNITY_ONE).userId(USER).tag("kafka").declaredAt(DECLARED).build(),
                Declaration.builder().communityId(COMMUNITY_TWO).userId(USER).tag("kafka").declaredAt(DECLARED)
                        .build()));
        when(communityAccess.summaryById(any())).thenReturn(Map.of(COMMUNITY_ONE, new CommunitySummary("beta", "Beta"),
                COMMUNITY_TWO, new CommunitySummary("alpha", "alpha")));

        // when / then
        assertThat(sut.selfView().declarations()).extracting(SelfDeclaration::communityName).containsExactly("alpha",
                "Beta");
    }

    @Test
    void selfView_resolvesTheCommunitiesOfSignalsAndDeclarationsInOneCall() {

        // given
        givenSignals(ExpertiseSignalMother.any().userId(USER).communityId(COMMUNITY_ONE).tag("java").build());
        when(declarationRepository.forUser(ORG, USER)).thenReturn(List.of(Declaration.builder()
                .communityId(COMMUNITY_TWO).userId(USER).tag("kafka").declaredAt(DECLARED).build()));

        // when
        sut.selfView();

        // then
        verify(communityAccess)
                .summaryById(argThat(ids -> Set.copyOf(ids).equals(Set.of(COMMUNITY_ONE, COMMUNITY_TWO))));
        verify(communityAccess, times(1)).summaryById(any());
    }

    @Test
    void selfView_leavesOutADeclarationWhoseCommunityIsGone() {

        // given
        when(expertiseQueryRepository.findForUser(ORG, USER)).thenReturn(List.of());
        when(declarationRepository.forUser(ORG, USER)).thenReturn(
                List.of(Declaration.builder().communityId(99L).userId(USER).tag("kafka").declaredAt(DECLARED).build()));

        // when / then
        assertThat(sut.selfView().declarations()).isEmpty();
    }

    @Test
    void selfView_listsTheCommunitiesTheUserCanDeclareIn() {

        // given
        when(expertiseQueryRepository.findForUser(ORG, USER)).thenReturn(List.of());
        var declarable = List.of(new DeclarableCommunity(COMMUNITY_TWO, "apis", "APIs"),
                new DeclarableCommunity(COMMUNITY_ONE, "platform", "Platform"));
        when(communityAccess.declarableCommunities(ORG, USER)).thenReturn(declarable);

        // when / then
        assertThat(sut.selfView().declarableCommunities()).isEqualTo(declarable);
    }

    private void givenSignals(ExpertiseSignal... signals) {

        when(expertiseQueryRepository.findForUser(ORG, USER))
                .thenReturn(List.of(new ExpertiseQueryRepository.UserSignals(USER, List.of(signals))));
    }

    private void givenVisible(ExpertiseSignal... signals) {

        when(expertiseQueryRepository.findVisibleForUser(ORG, USER)).thenReturn(List.of(signals));
    }

    // ------------------------------------------------------------------
    // selfView - community tiers
    // ------------------------------------------------------------------

    @Test
    void selfView_eachCommunityReadsItsOwnTierForTheArea() {

        when(communityAccess.summaryById(any())).thenReturn(
                Map.of(10L, new CommunitySummary("eng", "Engineering"), 20L, new CommunitySummary("plat", "Platform")));
        var presented = ExpertiseSignalMother.any().id(1L).userId(USER).tag("kafka").communityId(10L)
                .signalType(SignalType.PRESENTED).build();
        var contributed = ExpertiseSignalMother.any().id(2L).userId(USER).tag("kafka").communityId(20L)
                .signalType(SignalType.CONTRIBUTED).build();
        givenSignals(presented, contributed);
        givenVisible(presented, contributed);

        var kafka = sut.selfView().areas().get(0);

        assertThat(kafka.communityTiers()).containsExactly(new CommunityTier(10L, Tier.PRACTITIONER),
                new CommunityTier(20L, Tier.CONTRIBUTOR));
    }

    @Test
    void selfView_aCommunityTierReadsTheAreaThroughThatCommunitysAliases() {

        when(communityAccess.summaryById(any())).thenReturn(
                Map.of(10L, new CommunitySummary("eng", "Engineering"), 20L, new CommunitySummary("plat", "Platform")));
        tagAliasRepository.addAlias(ORG, 10L, "k8s", "kubernetes");
        var aliasedSignal = ExpertiseSignalMother.any().userId(USER).tag("k8s").communityId(10L)
                .signalType(SignalType.PRESENTED).build();
        var rawSignal = ExpertiseSignalMother.any().userId(USER).tag("k8s").communityId(20L)
                .signalType(SignalType.CONTRIBUTED).build();
        givenSignals(aliasedSignal, rawSignal);
        givenVisible(aliasedSignal, rawSignal);

        var view = sut.selfView();

        var kubernetes = view.areas().stream().filter(a -> a.canonicalTag().equals("kubernetes")).findFirst()
                .orElseThrow();
        assertThat(kubernetes.communityTiers()).containsExactly(new CommunityTier(10L, Tier.PRACTITIONER));

        var k8s = view.areas().stream().filter(a -> a.canonicalTag().equals("k8s")).findFirst().orElseThrow();
        assertThat(k8s.communityTiers()).containsExactly(new CommunityTier(20L, Tier.CONTRIBUTOR));
    }

    @Test
    void selfView_aSignalTheCommunityCannotSeeLowersOnlyItsCommunityTier() {

        when(communityAccess.summaryById(any())).thenReturn(Map.of(10L, new CommunitySummary("eng", "Engineering")));
        var dismissedTalk = ExpertiseSignalMother.any().id(1L).userId(USER).tag("kafka").communityId(10L)
                .signalType(SignalType.PRESENTED).build();
        var artifact = ExpertiseSignalMother.any().id(2L).userId(USER).tag("kafka").communityId(10L)
                .signalType(SignalType.CONTRIBUTED).build();
        givenSignals(dismissedTalk, artifact);
        givenVisible(artifact);

        var kafka = sut.selfView().areas().get(0);

        assertThat(kafka.tier()).isEqualTo(Tier.PRACTITIONER);
        assertThat(kafka.communityTiers()).containsExactly(new CommunityTier(10L, Tier.CONTRIBUTOR));
    }

    @Test
    void selfView_aCommunityWithNothingVisibleIsAbsentFromTheTiers() {

        when(communityAccess.summaryById(any())).thenReturn(Map.of(10L, new CommunitySummary("eng", "Engineering")));
        givenSignals(ExpertiseSignalMother.any().userId(USER).tag("kafka").communityId(10L)
                .signalType(SignalType.PRESENTED).build());

        assertThat(sut.selfView().areas().get(0).communityTiers()).isEmpty();
    }

    @Test
    void selfView_aCommunityThatNoLongerResolvesCarriesNoTier() {

        when(communityAccess.summaryById(any())).thenReturn(Map.of());
        var signal = ExpertiseSignalMother.any().userId(USER).tag("kafka").communityId(10L)
                .signalType(SignalType.PRESENTED).build();
        givenSignals(signal);
        givenVisible(signal);

        assertThat(sut.selfView().areas().get(0).communityTiers()).isEmpty();
    }

    // ------------------------------------------------------------------
    // selfViewIn / areaEvidenceIn - the self view inside one community
    // ------------------------------------------------------------------

    @Test
    void selfViewIn_readsTheCommunitysSignalsDeclarationsAndTiersAlone() {

        // given - a talk the community cannot see still counts for the owner's own tier
        when(communityAccess.communityIdForSlug("platform")).thenReturn(COMMUNITY_ONE);
        when(communityAccess.summaryById(any()))
                .thenReturn(Map.of(COMMUNITY_ONE, new CommunitySummary("platform", "Platform")));
        var dismissedTalk = ExpertiseSignalMother.any().id(1L).userId(USER).communityId(COMMUNITY_ONE).tag("kafka")
                .signalType(SignalType.PRESENTED).build();
        var artifact = ExpertiseSignalMother.any().id(2L).userId(USER).communityId(COMMUNITY_ONE).tag("kafka")
                .signalType(SignalType.CONTRIBUTED).build();
        when(expertiseQueryRepository.findForUserIn(ORG, COMMUNITY_ONE, USER))
                .thenReturn(List.of(dismissedTalk, artifact));
        when(expertiseQueryRepository.findVisibleForUserIn(ORG, COMMUNITY_ONE, USER)).thenReturn(List.of(artifact));
        when(declarationRepository.inCommunity(ORG, COMMUNITY_ONE, USER)).thenReturn(List.of(
                Declaration.builder().communityId(COMMUNITY_ONE).userId(USER).tag("ddd").declaredAt(DECLARED).build()));
        when(communityAccess.declarableCommunities(ORG, USER))
                .thenReturn(List.of(new DeclarableCommunity(COMMUNITY_TWO, "data", "Data"),
                        new DeclarableCommunity(COMMUNITY_ONE, "platform", "Platform")));

        // when
        var actual = sut.selfViewIn("platform");

        // then
        assertThat(actual).usingRecursiveComparison()
                .isEqualTo(new SelfView(false,
                        List.of(new SelfArea("kafka", Tier.PRACTITIONER, false,
                                List.of(new AreaCommunity(COMMUNITY_ONE, "platform", "Platform")),
                                TierPolicy.evidenceOf(List.of(dismissedTalk, artifact), 5),
                                List.of(new CommunityTier(COMMUNITY_ONE, Tier.CONTRIBUTOR)))),
                        List.of(SelfDeclaration.builder().tag("ddd").communityId(COMMUNITY_ONE)
                                .communityName("Platform").communitySlug("platform").hidden(false).declaredAt(DECLARED)
                                .build()),
                        List.of(new DeclarableCommunity(COMMUNITY_ONE, "platform", "Platform"))));
    }

    @Test
    void selfViewIn_neverReadsTheOrganizationWideSignalsOrDeclarations() {

        // given
        when(communityAccess.communityIdForSlug("platform")).thenReturn(COMMUNITY_ONE);

        // when
        sut.selfViewIn("platform");

        // then
        verify(expertiseQueryRepository, never()).findForUser(anyString(), anyString());
        verify(expertiseQueryRepository, never()).findVisibleForUser(anyString(), anyString());
        verify(declarationRepository, never()).forUser(anyString(), anyString());
    }

    @Test
    void selfViewIn_keepsTheOrganizationWideOptOutAndHiddenAreas() {

        // given
        when(communityAccess.communityIdForSlug("platform")).thenReturn(COMMUNITY_ONE);
        when(preferenceRepository.isOptedOut(ORG, USER)).thenReturn(true);
        when(preferenceRepository.hiddenTags(ORG, USER)).thenReturn(Set.of("kafka"));
        when(expertiseQueryRepository.findForUserIn(ORG, COMMUNITY_ONE, USER)).thenReturn(
                List.of(ExpertiseSignalMother.any().userId(USER).communityId(COMMUNITY_ONE).tag("kafka").build()));

        // when
        var actual = sut.selfViewIn("platform");

        // then
        assertThat(actual.optedOut()).isTrue();
        assertThat(actual.areas()).singleElement().extracting(SelfArea::hidden).isEqualTo(true);
    }

    @Test
    void areaEvidenceIn_pagesTheCommunitysEvidenceAlone() {

        // given - "k8s" reads as "kubernetes" in the community the page is about
        when(communityAccess.communityIdForSlug("platform")).thenReturn(COMMUNITY_ONE);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ONE, "k8s", "kubernetes");
        var signals = IntStream.range(0, 3).mapToObj(i -> ExpertiseSignalMother.any().userId(USER)
                .communityId(COMMUNITY_ONE).tag("k8s").sourceId((long) i).build()).toList();
        when(expertiseQueryRepository.findForUserIn(ORG, COMMUNITY_ONE, USER)).thenReturn(signals);

        // when
        var actual = sut.areaEvidenceIn("platform", "Kubernetes", 0, 2);

        // then
        assertThat(actual.items()).hasSize(2);
        assertThat(actual.hasMore()).isTrue();
        verify(expertiseQueryRepository, never()).findForUser(anyString(), anyString());
    }
}
