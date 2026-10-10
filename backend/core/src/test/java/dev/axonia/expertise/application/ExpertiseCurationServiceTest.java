package dev.axonia.expertise.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.community.api.TagUsage;
import dev.axonia.community.api.TagUsageKind;
import dev.axonia.community.api.spi.FakeCommunityTagWriter;
import dev.axonia.community.api.spi.FakeCommunityTagWriter.Removal;
import dev.axonia.community.api.spi.FakeCommunityTagWriter.Rename;
import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.CurrentUser;
import dev.axonia.expertise.application.port.out.DeclarationRepository;
import dev.axonia.expertise.application.port.out.ExpertiseQueryRepository;
import dev.axonia.expertise.application.port.out.FakeNamedAreaRepository;
import dev.axonia.expertise.application.port.out.FakeTagAliasRepository;
import dev.axonia.expertise.application.port.out.FakeTagDismissalRepository;
import dev.axonia.expertise.application.port.out.OrganizationContext;
import dev.axonia.expertise.application.port.out.SignalProjection;
import dev.axonia.expertise.application.port.out.VocabularyLock;
import dev.axonia.expertise.domain.Declaration;
import dev.axonia.expertise.domain.ExpertiseArea;
import dev.axonia.expertise.domain.NamedArea;
import dev.axonia.expertise.domain.TagPair;
import dev.axonia.expertise.domain.UserTagAggregate;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class ExpertiseCurationServiceTest {

    private static final String ORG = "org-1";
    private static final String SLUG = "eng";
    private static final String OTHER_SLUG = "data";
    private static final long COMMUNITY_ID = 1L;
    private static final long OTHER_COMMUNITY_ID = 2L;
    private static final String LEADER = "alice";
    private static final String NON_LEADER = "bob";

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private ExpertiseQueryRepository expertiseQueryRepository;
    @Mock
    private SignalProjection signalProjection;
    @Mock
    private DeclarationRepository declarationRepository;
    @Mock
    private VocabularyLock vocabularyLock;

    private FakeTagAliasRepository tagAliasRepository;
    private FakeTagDismissalRepository tagDismissalRepository;
    private FakeCommunityTagWriter artifactWriter;
    private FakeCommunityTagWriter eventsWriter;
    private FakeNamedAreaRepository namedAreaRepository;

    private ExpertiseCurationService sut;

    @BeforeEach
    void setUp() {

        tagAliasRepository = new FakeTagAliasRepository();
        tagDismissalRepository = new FakeTagDismissalRepository();
        artifactWriter = new FakeCommunityTagWriter();
        eventsWriter = new FakeCommunityTagWriter();
        namedAreaRepository = new FakeNamedAreaRepository();
        var curationScope = new CurationScope(currentUser, communityAccess, expertiseQueryRepository,
                declarationRepository, tagAliasRepository, List.of(artifactWriter, eventsWriter));
        sut = new ExpertiseCurationService(organizationContext, curationScope, expertiseQueryRepository,
                tagAliasRepository, tagDismissalRepository, List.of(artifactWriter, eventsWriter), signalProjection,
                declarationRepository, namedAreaRepository, vocabularyLock);

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(communityAccess.communityIdForSlug(SLUG)).thenReturn(COMMUNITY_ID);
        when(communityAccess.communityIdForSlug(OTHER_SLUG)).thenReturn(OTHER_COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of());
        when(expertiseQueryRepository.allTags(ORG, OTHER_COMMUNITY_ID)).thenReturn(List.of());
    }

    // -----------------------------------------------------------------------
    // Curation gate - honours canCurateTags axis
    // -----------------------------------------------------------------------

    @Test
    void split_deniedWhenCurationClosedToMember() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(false);

        assertThatThrownBy(() -> sut.split(SLUG, "kubernetes")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void split_allowedWhenCurationOpenToMember() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(true);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes"));

        sut.split(SLUG, "kubernetes"); // no throw
    }

    @Test
    void split_allowedWhenOrganizer() {

        when(currentUser.userId()).thenReturn(LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, LEADER)).thenReturn(true);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes"));

        sut.split(SLUG, "kubernetes"); // no throw
    }

    // -----------------------------------------------------------------------
    // Curation guard - all three methods (organizer path via canCurateTags)
    // -----------------------------------------------------------------------

    @Test
    void suggestions_nonLeader_throwsForbidden() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(false);

        assertThatThrownBy(() -> sut.suggestions(SLUG)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void merge_nonLeader_throwsForbidden() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(false);

        assertThatThrownBy(() -> sut.merge(SLUG, "k8s", "kubernetes")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void split_nonLeader_throwsForbidden() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(false);

        assertThatThrownBy(() -> sut.split(SLUG, "k8s")).isInstanceOf(ForbiddenException.class);
    }

    // -----------------------------------------------------------------------
    // merge - happy path
    // -----------------------------------------------------------------------

    @Test
    void merge_writesAlias() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("k8s", "kubernetes"));

        sut.merge(SLUG, "k8s", "kubernetes");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsEntry("k8s", "kubernetes");
    }

    @Test
    void merge_normalizesVariantAndCanonical() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("k8s", "kubernetes"));

        // "K8S" normalizes to "k8s"; "Kubernetes" normalizes to "kubernetes"
        sut.merge(SLUG, "K8S", "Kubernetes");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsEntry("k8s", "kubernetes");
    }

    @Test
    void aMergeInOneCommunityLeavesAnotherCommunitysGraphAlone() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak", "kafka"));

        sut.merge(SLUG, "kafak", "kafka");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsKey("kafak");
        assertThat(tagAliasRepository.aliasMap(ORG, OTHER_COMMUNITY_ID)).isEmpty();
    }

    @Test
    void theSameTagMayResolveDifferentlyInTwoCommunities() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        givenLeaderOf(OTHER_SLUG, OTHER_COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka", "apache-kafka"));
        when(expertiseQueryRepository.allTags(ORG, OTHER_COMMUNITY_ID)).thenReturn(List.of("kafka", "streaming"));

        sut.merge(SLUG, "kafka", "apache-kafka");
        sut.merge(OTHER_SLUG, "kafka", "streaming");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsExactly(entry("kafka", "apache-kafka"));
        assertThat(tagAliasRepository.aliasMap(ORG, OTHER_COMMUNITY_ID)).containsExactly(entry("kafka", "streaming"));
    }

    // -----------------------------------------------------------------------
    // merge - self-merge rejected
    // -----------------------------------------------------------------------

    @Test
    void merge_selfMerge_throwsValidationException() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));

        assertThatThrownBy(() -> sut.merge(SLUG, "kafka", "kafka")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("kafka");
    }

    @Test
    void merge_selfMergeAfterNormalization_throwsValidationException() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));

        assertThatThrownBy(() -> sut.merge(SLUG, "Kafka", "kafka")).isInstanceOf(ValidationException.class);
    }

    // -----------------------------------------------------------------------
    // merge - cycle rejected
    // -----------------------------------------------------------------------

    @Test
    void merge_cycleCanonicalIsAlreadyVariant_throwsValidationException() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        // canonical "b" is itself a variant pointing to "c": adding a -> b would build a chain
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "b", "c");

        assertThatThrownBy(() -> sut.merge(SLUG, "a", "b")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("b");
    }

    @Test
    void merge_cycleVariantIsAlreadyCanonical_throwsValidationException() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        // "a" is already the canonical of "b", so a -> c would build the chain b -> a -> c
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "b", "a");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("a", "c"));

        assertThatThrownBy(() -> sut.merge(SLUG, "a", "c")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("a");
    }

    @Test
    void merge_isBlindToAnotherCommunitysCycle() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        // The other community merged "b" into "c"; that says nothing about this community's graph.
        tagAliasRepository.addAlias(ORG, OTHER_COMMUNITY_ID, "b", "c");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("a", "b"));

        sut.merge(SLUG, "a", "b");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsExactly(entry("a", "b"));
    }

    // -----------------------------------------------------------------------
    // split - happy path
    // -----------------------------------------------------------------------

    @Test
    void split_carriesThePrivacyChoicesAboutTheAreaToTheVariant() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "k8s", "kubernetes");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("k8s"));

        sut.split(SLUG, "k8s");

        verify(signalProjection).carryOverSplit(ORG, COMMUNITY_ID, "k8s", "kubernetes");
    }

    @Test
    void split_ofATagThatIsNoVariantCarriesNothing() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("k8s"));

        sut.split(SLUG, "k8s");

        verify(signalProjection, never()).carryOverSplit(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void mergeMany_movingAVariantToAnotherAreaCarriesThePrivacyChoicesAboutTheOldOne() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "k8s", "kubernetes");
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kafak", "kafka");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID))
                .thenReturn(List.of("k8s", "kubernetes", "kafak", "kafka", "containers"));

        sut.mergeMany(SLUG, List.of("k8s", "kafak"), "containers");

        var order = inOrder(signalProjection);
        order.verify(signalProjection).carryOverSplit(ORG, COMMUNITY_ID, "k8s", "kubernetes");
        order.verify(signalProjection).carryHiddenArea(ORG, COMMUNITY_ID, "k8s", "containers");
        order.verify(signalProjection).carryOverSplit(ORG, COMMUNITY_ID, "kafak", "kafka");
        order.verify(signalProjection).carryHiddenArea(ORG, COMMUNITY_ID, "kafak", "containers");
        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsEntry("k8s", "containers");
    }

    @Test
    void mergeAndSplitLockTheCommunityBeforeCarryingThePrivacyChoices() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("k8s", "kubernetes"));

        sut.merge(SLUG, "k8s", "kubernetes");
        sut.split(SLUG, "k8s");

        var order = inOrder(vocabularyLock, signalProjection);
        order.verify(vocabularyLock).lock(ORG, COMMUNITY_ID);
        order.verify(signalProjection).carryHiddenArea(ORG, COMMUNITY_ID, "k8s", "kubernetes");
        order.verify(vocabularyLock).lock(ORG, COMMUNITY_ID);
        order.verify(signalProjection).carryOverSplit(ORG, COMMUNITY_ID, "k8s", "kubernetes");
    }

    @Test
    void mergeMany_remergingIntoTheSameAreaCarriesNothing() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "k8s", "kubernetes");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("k8s", "kubernetes"));

        sut.mergeMany(SLUG, List.of("k8s"), "kubernetes");

        verify(signalProjection, never()).carryOverSplit(anyString(), anyLong(), anyString(), anyString());
        verify(signalProjection, never()).carryHiddenArea(anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void split_removesAlias() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "k8s", "kubernetes");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("k8s"));

        sut.split(SLUG, "k8s");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void split_normalizesVariant() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "spring-boot", "spring");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("spring-boot"));

        sut.split(SLUG, "Spring Boot");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void split_rejectsTagOutsideTheCommunityVocabulary() {

        // given
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("java"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kube", "kubernetes");

        // when / then
        assertThatThrownBy(() -> sut.split(SLUG, "kube")).isInstanceOf(ValidationException.class);
        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsEntry("kube", "kubernetes");
    }

    @Test
    void split_allowsAVariantOfACommunityCanonical() {

        // given - the community sees "apache-kafka" because its raw "kafka" is aliased to it
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kafka", "apache-kafka");

        // when
        sut.split(SLUG, "kafka");

        // then
        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void split_leavesTheOtherCommunitysAliasAlone() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "k8s", "kubernetes");
        tagAliasRepository.addAlias(ORG, OTHER_COMMUNITY_ID, "k8s", "kubernetes");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("k8s"));

        sut.split(SLUG, "k8s");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).isEmpty();
        assertThat(tagAliasRepository.aliasMap(ORG, OTHER_COMMUNITY_ID)).containsEntry("k8s", "kubernetes");
    }

    // -----------------------------------------------------------------------
    // vocabulary - the names a curator works on, counted as the search counts
    // -----------------------------------------------------------------------

    @Test
    void vocabulary_nonLeader_throwsForbidden() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(false);

        assertThatThrownBy(() -> sut.vocabulary(SLUG)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void vocabulary_drawsANameNobodyVisibleCarries_withACountOfZero() {

        // given - "helm" was declared by somebody the search no longer lists, "kafka" is carried by bob
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));
        when(declarationRepository.tagsIn(ORG, COMMUNITY_ID)).thenReturn(List.of("helm"));
        when(expertiseQueryRepository.userTagAggregates(ORG, COMMUNITY_ID))
                .thenReturn(List.of(aggregate("bob", "kafka")));

        // when
        var result = sut.vocabulary(SLUG);

        // then
        assertThat(result).extracting(ExpertiseArea::canonicalTag, ExpertiseArea::peopleCount)
                .containsExactly(tuple("helm", 0), tuple("kafka", 1));
    }

    @Test
    void vocabulary_drawsAChipForEverySpellingTheCommunityStillCarries() {

        // given - "helm" is merged into "kubernetes" and only dana, who is hidden from the search, carries it
        givenLeaderOf(SLUG, COMMUNITY_ID);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "helm", "kubernetes");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes"));
        when(declarationRepository.tagsIn(ORG, COMMUNITY_ID)).thenReturn(List.of("helm"));
        when(expertiseQueryRepository.userTagAggregates(ORG, COMMUNITY_ID))
                .thenReturn(List.of(aggregate("bob", "kubernetes")));

        // when
        var result = sut.vocabulary(SLUG);

        // then - the chip the merge rule keeps alive is on the card, so a curator can split it
        assertThat(result).singleElement()
                .satisfies(area -> assertThat(area.variants()).containsExactly("helm", "kubernetes"));
    }

    @Test
    void vocabulary_countsAPersonOnceAcrossTheirSignalAndTheirDeclaration() {

        // given
        givenLeaderOf(SLUG, COMMUNITY_ID);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "k8s", "kubernetes");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes", "k8s"));
        when(expertiseQueryRepository.userTagAggregates(ORG, COMMUNITY_ID))
                .thenReturn(List.of(aggregate("bob", "k8s")));
        when(declarationRepository.allVisibleIn(ORG, COMMUNITY_ID)).thenReturn(List.of(Declaration.builder()
                .communityId(COMMUNITY_ID).userId("bob").tag("kubernetes").declaredAt(Instant.EPOCH).build()));

        // when
        var result = sut.vocabulary(SLUG);

        // then
        assertThat(result).singleElement().extracting(ExpertiseArea::peopleCount).isEqualTo(1);
    }

    private static UserTagAggregate aggregate(String userId, String tag) {

        return new UserTagAggregate(userId, tag, 1, 0, 0, Instant.EPOCH);
    }

    // -----------------------------------------------------------------------
    // suggestions - maps ScoredPairs to MergeSuggestions
    // -----------------------------------------------------------------------

    @Test
    void suggestions_mapsScoredPairsToMergeSuggestions() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka", "kafk", "java"));

        var result = sut.suggestions(SLUG);

        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(s -> {
            assertThat(s.tagA()).isNotBlank();
            assertThat(s.tagB()).isNotBlank();
            assertThat(s.score()).isBetween(0.0, 1.0);
        });
        // "kafka" and "kafk" are very similar, so expect them to be suggested
        assertThat(result).anyMatch(s -> (s.tagA().equals("kafka") && s.tagB().equals("kafk"))
                || (s.tagA().equals("kafk") && s.tagB().equals("kafka")));
    }

    @Test
    void suggestions_emptyTags_returnsEmpty() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of());

        assertThat(sut.suggestions(SLUG)).isEmpty();
    }

    @Test
    void suggestions_collapsesMergedVariantsToCanonicalBeforeComparing() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        // "apache-kafka" has already been merged into "kafka"; its signal rows still
        // carry the raw tag, so allTags returns it alongside the canonical.
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "apache-kafka", "kafka");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID))
                .thenReturn(List.of("apache", "apache-kafka", "kafka"));

        var result = sut.suggestions(SLUG);

        // A merged variant must never reappear as a suggestion candidate.
        assertThat(result).noneMatch(s -> s.tagA().equals("apache-kafka") || s.tagB().equals("apache-kafka"));
    }

    @Test
    void suggestions_doesNotSuggestMergingAVariantBackIntoItsOwnCanonical() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "apache-kafka", "kafka");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("apache-kafka", "kafka"));

        // Both collapse to "kafka": a single canonical, so there is nothing to suggest.
        assertThat(sut.suggestions(SLUG)).isEmpty();
    }

    @Test
    void suggestions_readTheirOwnCommunitysVocabulary() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        // The other community merged the variant away; this community never did, so it still sees both.
        tagAliasRepository.addAlias(ORG, OTHER_COMMUNITY_ID, "apache-kafka", "kafka");
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("apache-kafka", "kafka"));

        assertThat(sut.suggestions(SLUG)).anyMatch(s -> (s.tagA().equals("kafka") && s.tagB().equals("apache-kafka"))
                || (s.tagA().equals("apache-kafka") && s.tagB().equals("kafka")));
    }

    // -----------------------------------------------------------------------
    // mergeMany - batch merge
    // -----------------------------------------------------------------------

    @Test
    void mergeMany_writesAllAliasesToTheSurvivor() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("yep", "ok", "yes"));

        sut.mergeMany(SLUG, List.of("yep", "ok"), "yes");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsOnly(entry("yep", "yes"),
                entry("ok", "yes"));
    }

    @Test
    void mergeMany_normalizesVariantsAndCanonical() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("k8s", "kubernetes"));

        sut.mergeMany(SLUG, List.of("K8S"), "Kubernetes");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsEntry("k8s", "kubernetes");
    }

    @Test
    void mergeMany_rejectsTagsOutsideTheCommunityVocabulary() {

        // given - the community's signals know java and jvm, nothing else
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("java", "jvm"));

        // when / then - merging a foreign community's tag is refused
        assertThatThrownBy(() -> sut.mergeMany(SLUG, List.of("kubernetes"), "java"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("kubernetes");
        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void mergeMany_rejectsSelfMergeInBatch_writesNothing() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("ok", "yes"));

        assertThatThrownBy(() -> sut.mergeMany(SLUG, List.of("ok", "yes"), "yes"))
                .isInstanceOf(ValidationException.class);
        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void mergeMany_rejectsCanonicalThatIsAlreadyAVariant() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "b", "c");

        assertThatThrownBy(() -> sut.mergeMany(SLUG, List.of("a"), "b")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("b");
        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsExactly(entry("b", "c"));
    }

    @Test
    void mergeMany_nonLeader_throwsForbidden() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(false);

        assertThatThrownBy(() -> sut.mergeMany(SLUG, List.of("ok"), "yes")).isInstanceOf(ForbiddenException.class);
    }

    // -----------------------------------------------------------------------
    // dismiss / undismiss - park a look-alike pair as "won't do"
    // -----------------------------------------------------------------------

    @Test
    void dismiss_storesOrderNormalizedPair() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("spring-boot", "spring"));

        sut.dismiss(SLUG, "spring-boot", "spring"); // deliberately reversed

        assertThat(tagDismissalRepository.dismissedPairs(ORG, COMMUNITY_ID))
                .containsExactly(new TagPair("spring", "spring-boot"));
    }

    @Test
    void dismiss_normalizesTagsBeforeStoring() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("spring-boot", "spring"));

        sut.dismiss(SLUG, "Spring Boot", "Spring");

        assertThat(tagDismissalRepository.dismissedPairs(ORG, COMMUNITY_ID))
                .containsExactly(new TagPair("spring", "spring-boot"));
    }

    @Test
    void aParkedPairIsAVerdictOfTheParkingCommunityAlone() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("spring-boot", "spring"));

        sut.dismiss(SLUG, "spring", "spring-boot");

        assertThat(tagDismissalRepository.dismissedPairs(ORG, COMMUNITY_ID)).isNotEmpty();
        assertThat(tagDismissalRepository.dismissedPairs(ORG, OTHER_COMMUNITY_ID)).isEmpty();
    }

    @Test
    void dismiss_selfPair_throwsValidationException() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("spring"));

        assertThatThrownBy(() -> sut.dismiss(SLUG, "Spring", "spring")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("spring");
    }

    @Test
    void dismiss_nonLeader_throwsForbidden() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(false);

        assertThatThrownBy(() -> sut.dismiss(SLUG, "spring", "spring-boot")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void dismiss_rejectsTagOutsideTheCommunityVocabulary() {

        // given
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("java"));

        // when / then
        assertThatThrownBy(() -> sut.dismiss(SLUG, "java", "terraform")).isInstanceOf(ValidationException.class);
        assertThat(tagDismissalRepository.dismissedPairs(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void undismiss_removesOrderNormalizedPair() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("spring-boot", "spring"));
        tagDismissalRepository.addDismissal(ORG, COMMUNITY_ID, "spring", "spring-boot");

        sut.undismiss(SLUG, "spring-boot", "spring"); // reversed

        assertThat(tagDismissalRepository.dismissedPairs(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void undismiss_leavesTheOtherCommunitysVerdictAlone() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("spring-boot", "spring"));
        tagDismissalRepository.addDismissal(ORG, COMMUNITY_ID, "spring", "spring-boot");
        tagDismissalRepository.addDismissal(ORG, OTHER_COMMUNITY_ID, "spring", "spring-boot");

        sut.undismiss(SLUG, "spring", "spring-boot");

        assertThat(tagDismissalRepository.dismissedPairs(ORG, COMMUNITY_ID)).isEmpty();
        assertThat(tagDismissalRepository.dismissedPairs(ORG, OTHER_COMMUNITY_ID))
                .containsExactly(new TagPair("spring", "spring-boot"));
    }

    @Test
    void undismiss_nonLeader_throwsForbidden() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(false);

        assertThatThrownBy(() -> sut.undismiss(SLUG, "spring", "spring-boot")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void suggestions_excludesDismissedPair_regardlessOfOrder() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka", "kafk", "java"));
        // "kafka"/"kafk" would normally be suggested; park it (stored reversed to prove order-independence)
        tagDismissalRepository.addDismissal(ORG, COMMUNITY_ID, "kafk", "kafka");

        var result = sut.suggestions(SLUG);

        assertThat(result).noneMatch(s -> (s.tagA().equals("kafka") && s.tagB().equals("kafk"))
                || (s.tagA().equals("kafk") && s.tagB().equals("kafka")));
    }

    @Test
    void suggestions_keepsNonDismissedPairs() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka", "kafk"));
        tagDismissalRepository.addDismissal(ORG, COMMUNITY_ID, "react", "recat");

        assertThat(sut.suggestions(SLUG)).isNotEmpty();
    }

    @Test
    void suggestions_seeATagOnlyADeclarationCarries_untilThePairIsParked() {

        // given - the content says "kafka", a member declared the typo
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));
        when(declarationRepository.tagsIn(ORG, COMMUNITY_ID)).thenReturn(List.of("kafk"));

        // then - the typo is a candidate against the name the community already uses
        assertThat(sut.suggestions(SLUG)).anyMatch(s -> (s.tagA().equals("kafka") && s.tagB().equals("kafk"))
                || (s.tagA().equals("kafk") && s.tagB().equals("kafka")));

        // when - the curator parks it
        sut.dismiss(SLUG, "kafk", "kafka");

        // then
        assertThat(sut.suggestions(SLUG)).isEmpty();
    }

    @Test
    void suggestions_ignoreAnotherCommunitysParkedPair() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka", "kafk"));
        tagDismissalRepository.addDismissal(ORG, OTHER_COMMUNITY_ID, "kafk", "kafka");

        assertThat(sut.suggestions(SLUG)).isNotEmpty();
    }

    // -----------------------------------------------------------------------
    // rename - rewrites the community's real content, not just the overlay
    // -----------------------------------------------------------------------

    @Test
    void renamingRewritesEveryContextsContent() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak"));

        sut.rename(SLUG, "kafak", "kafka");

        assertThat(artifactWriter.renames()).containsExactly(new Rename(COMMUNITY_ID, "kafak", "kafka"));
        assertThat(eventsWriter.renames()).containsExactly(new Rename(COMMUNITY_ID, "kafak", "kafka"));
        verify(signalProjection).renameTag(ORG, COMMUNITY_ID, "kafak", "kafka");
        verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "kafak", "kafka");
    }

    @Test
    void renamingCarriesTheHiddenAreaBeforeRenamingTheSignalsAndDeclarations() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak"));

        sut.rename(SLUG, "kafak", "kafka");

        var order = inOrder(signalProjection, declarationRepository);
        order.verify(signalProjection).carryHiddenArea(ORG, COMMUNITY_ID, "kafak", "kafka");
        order.verify(signalProjection).renameTag(ORG, COMMUNITY_ID, "kafak", "kafka");
        order.verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "kafak", "kafka");
    }

    @Test
    void renamingCarriesEverySpellingThatReadsAsTheTagOntoTheNewName() {

        // given - "k8s" is merged into "kubernetes" with no signal of its own, and members declared it as typed
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "k8s", "kubernetes");

        // when
        sut.rename(SLUG, "kubernetes", "containers");

        // then
        var order = inOrder(signalProjection, declarationRepository);
        order.verify(signalProjection).carryHiddenArea(ORG, COMMUNITY_ID, "kubernetes", "containers");
        order.verify(signalProjection).renameTag(ORG, COMMUNITY_ID, "k8s", "containers");
        order.verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "k8s", "containers");
        order.verify(signalProjection).renameTag(ORG, COMMUNITY_ID, "kubernetes", "containers");
        order.verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "kubernetes", "containers");
    }

    @Test
    void renamingOntoAVariantOfItselfFoldsThatSpellingIntoTheTagFirst() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "k8s", "kubernetes");

        sut.rename(SLUG, "kubernetes", "k8s");

        var order = inOrder(signalProjection, declarationRepository);
        order.verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "k8s", "kubernetes");
        order.verify(signalProjection).renameTag(ORG, COMMUNITY_ID, "kubernetes", "k8s");
        order.verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "kubernetes", "k8s");
        verify(signalProjection, never()).renameTag(eq(ORG), anyLong(), eq("k8s"), anyString());
        verify(declarationRepository, never()).renameTag(ORG, COMMUNITY_ID, "k8s", "k8s");
    }

    @Test
    void renamingOntoAVariantOfAnotherTagLeavesThatSpellingsDeclarationsWithTheOtherTag() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka", "streams"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kafak", "kafka");

        sut.rename(SLUG, "streams", "kafak");

        var order = inOrder(signalProjection, declarationRepository);
        order.verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "kafak", "kafka");
        order.verify(signalProjection).renameTag(ORG, COMMUNITY_ID, "streams", "kafak");
        order.verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "streams", "kafak");
    }

    @Test
    void renamingOntoAMergedTagKeepsItsMergesSoEverySpellingStillReadsAsIt() {

        // given - "kafak" carries signals and is merged into "kafka"
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak", "kafka", "streams"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kafak", "kafka");

        // when
        sut.rename(SLUG, "streams", "kafka");

        // then
        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsExactly(entry("kafak", "kafka"));
        verify(signalProjection, never()).renameTag(eq(ORG), anyLong(), eq("kafak"), anyString());
        verify(declarationRepository, never()).renameTag(eq(ORG), anyLong(), eq("kafak"), anyString());
    }

    @Test
    void renamingAVariantLeavesItsDeclarationsWithItsCanonical() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "k8s", "kubernetes");

        sut.rename(SLUG, "k8s", "kube");

        var order = inOrder(signalProjection, declarationRepository);
        order.verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "k8s", "kubernetes");
        order.verify(signalProjection).renameTag(ORG, COMMUNITY_ID, "k8s", "kube");
        verify(declarationRepository, never()).renameTag(eq(ORG), anyLong(), eq("kubernetes"), anyString());
    }

    @Test
    void renamingLocksTheCommunityAgainstDeclaresBeforeReadingItsVocabulary() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak"));

        sut.rename(SLUG, "kafak", "kafka");

        var order = inOrder(vocabularyLock, expertiseQueryRepository, signalProjection);
        order.verify(vocabularyLock).lock(ORG, COMMUNITY_ID);
        order.verify(expertiseQueryRepository).allTags(ORG, COMMUNITY_ID);
        order.verify(signalProjection).renameTag(ORG, COMMUNITY_ID, "kafak", "kafka");
    }

    @Test
    void deletingLocksTheCommunityAgainstDeclaresBeforeReadingItsVocabulary() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("misc"));

        sut.delete(SLUG, "misc");

        var order = inOrder(vocabularyLock, expertiseQueryRepository, signalProjection);
        order.verify(vocabularyLock).lock(ORG, COMMUNITY_ID);
        order.verify(expertiseQueryRepository).allTags(ORG, COMMUNITY_ID);
        order.verify(signalProjection).removeTag(ORG, COMMUNITY_ID, "misc");
    }

    @Test
    void theNewNameIsNormalisedTheWayAnIngestedTagIs() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak"));

        sut.rename(SLUG, "kafak", "Apache Kafka!");

        assertThat(artifactWriter.renames()).containsExactly(new Rename(COMMUNITY_ID, "kafak", "apache-kafka"));
    }

    @Test
    void aVariantCannotBeRenamed() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak", "kafka"));
        sut.merge(SLUG, "kafak", "kafka");

        assertThatThrownBy(() -> sut.rename(SLUG, "kafak", "apache-kafka")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Split");
        assertThat(artifactWriter.renames()).isEmpty();
    }

    @Test
    void aTagWithVariantsCannotBeRenamed() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak", "kafka"));
        sut.merge(SLUG, "kafak", "kafka");

        assertThatThrownBy(() -> sut.rename(SLUG, "kafka", "apache-kafka")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Split");
        assertThat(artifactWriter.renames()).isEmpty();
    }

    @Test
    void aNameLongerThanTheSourceColumnIsRejected() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak"));

        assertThatThrownBy(() -> sut.rename(SLUG, "kafak", "a".repeat(51))).isInstanceOf(ValidationException.class)
                .hasMessageContaining("50");
    }

    @Test
    void aNameThatNormalisesToNothingIsRejected() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak"));

        assertThatThrownBy(() -> sut.rename(SLUG, "kafak", "!!!")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("name");
    }

    @Test
    void renamingATagOntoItselfIsRejected() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));

        assertThatThrownBy(() -> sut.rename(SLUG, "kafka", "Kafka")).isInstanceOf(ValidationException.class);
        assertThat(artifactWriter.renames()).isEmpty();
    }

    @Test
    void renamingOntoAVariantOfAnotherTagIsRejected() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak", "kafka", "streams"));
        sut.merge(SLUG, "kafak", "kafka");

        assertThatThrownBy(() -> sut.rename(SLUG, "streams", "kafak")).isInstanceOf(ValidationException.class);
        assertThat(artifactWriter.renames()).isEmpty();
    }

    @Test
    void renamingATagOutsideTheCommunityVocabularyIsRejected() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("java"));

        assertThatThrownBy(() -> sut.rename(SLUG, "kafak", "kafka")).isInstanceOf(ValidationException.class);
        assertThat(artifactWriter.renames()).isEmpty();
    }

    @Test
    void theTagBeingRenamedIsWeighedBeforeTheNameItIsGiven() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("java"));

        assertThatThrownBy(() -> sut.rename(SLUG, "kafak", "a".repeat(51))).isInstanceOfSatisfying(
                ValidationException.class, e -> assertThat(e.getCode()).isEqualTo("TAG_NOT_IN_COMMUNITY"));
    }

    @Test
    void renamingDropsTheParkedPairsThatNameTheOldTag() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak", "kafka"));
        sut.dismiss(SLUG, "kafak", "kafka");

        sut.rename(SLUG, "kafak", "kafka");

        assertThat(tagDismissalRepository.dismissedPairs(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void renamingKeepsTheParkedPairsThatDoNotNameTheOldTag() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak", "kafka", "react"));
        sut.dismiss(SLUG, "kafka", "react");

        sut.rename(SLUG, "kafak", "kafka");

        assertThat(tagDismissalRepository.dismissedPairs(ORG, COMMUNITY_ID))
                .containsExactly(new TagPair("kafka", "react"));
    }

    @Test
    void renamingCarriesTheTagsNamedAreaMembershipToTheNewName() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak", "misc"));
        var messaging = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");
        namedAreaRepository.place(ORG, COMMUNITY_ID, messaging, "kafak");
        sut.dismiss(SLUG, "kafak", "misc");

        sut.rename(SLUG, "kafak", "kafka");

        // the rename retires the vocabulary rows naming the old tag, and the membership is not one of them
        assertThat(tagDismissalRepository.dismissedPairs(ORG, COMMUNITY_ID)).isEmpty();
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID))
                .containsExactly(new NamedArea(messaging, "Messaging", List.of("kafka")));
    }

    @Test
    void renamingOntoATagAlreadyInANamedAreaKeepsThatPlacement() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak", "kafka"));
        var messaging = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");
        var streaming = namedAreaRepository.create(ORG, COMMUNITY_ID, "Streaming");
        namedAreaRepository.place(ORG, COMMUNITY_ID, messaging, "kafak");
        namedAreaRepository.place(ORG, COMMUNITY_ID, streaming, "kafka");

        sut.rename(SLUG, "kafak", "kafka");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).containsExactly(
                new NamedArea(messaging, "Messaging", List.of()),
                new NamedArea(streaming, "Streaming", List.of("kafka")));
    }

    // -----------------------------------------------------------------------
    // delete - retires a tag from the community's content
    // -----------------------------------------------------------------------

    @Test
    void deletingStripsTheTagFromEveryContext() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("misc"));

        sut.delete(SLUG, "misc");

        assertThat(artifactWriter.removals()).containsExactly(new Removal(COMMUNITY_ID, "misc"));
        assertThat(eventsWriter.removals()).containsExactly(new Removal(COMMUNITY_ID, "misc"));
        verify(signalProjection).removeTag(ORG, COMMUNITY_ID, "misc");
        verify(declarationRepository).removeTag(ORG, COMMUNITY_ID, "misc");
    }

    @Test
    void deletingDropsTheParkedPairsThatNameTheTag() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("misc", "misk"));
        sut.dismiss(SLUG, "misc", "misk");

        sut.delete(SLUG, "misc");

        assertThat(tagDismissalRepository.dismissedPairs(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void deletingDropsTheHiddenAreaBeforeRemovingEverySpelling() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kafak", "kafka");

        sut.delete(SLUG, "kafka");

        var order = inOrder(signalProjection, declarationRepository);
        order.verify(signalProjection).dropHiddenArea(ORG, COMMUNITY_ID, "kafka");
        order.verify(signalProjection).removeTag(ORG, COMMUNITY_ID, "kafak");
        order.verify(declarationRepository).removeTag(ORG, COMMUNITY_ID, "kafak");
        order.verify(signalProjection).removeTag(ORG, COMMUNITY_ID, "kafka");
        order.verify(declarationRepository).removeTag(ORG, COMMUNITY_ID, "kafka");
    }

    @Test
    void deletingATagLeavesNoNamedAreaMembershipBehind() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("misc", "kafka"));
        var messaging = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");
        namedAreaRepository.place(ORG, COMMUNITY_ID, messaging, "misc");
        namedAreaRepository.place(ORG, COMMUNITY_ID, messaging, "kafka");

        sut.delete(SLUG, "misc");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID))
                .containsExactly(new NamedArea(messaging, "Messaging", List.of("kafka")));
    }

    @Test
    void aNamedAreaEmptiedByATagDeleteStillExists() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("misc"));
        var leftovers = namedAreaRepository.create(ORG, COMMUNITY_ID, "Leftovers");
        namedAreaRepository.place(ORG, COMMUNITY_ID, leftovers, "misc");

        sut.delete(SLUG, "misc");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID))
                .containsExactly(new NamedArea(leftovers, "Leftovers", List.of()));
    }

    @Test
    void deletingATagLeavesNoMembershipUnderAnySpellingItRemoves() {

        // given - "kafak" was placed before it was merged into "kafka", and no signal carries it any more
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kafak", "kafka");
        var messaging = namedAreaRepository.create(ORG, COMMUNITY_ID, "Messaging");
        namedAreaRepository.place(ORG, COMMUNITY_ID, messaging, "kafak");

        // when
        sut.delete(SLUG, "kafka");

        // then
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID))
                .containsExactly(new NamedArea(messaging, "Messaging", List.of()));
    }

    @Test
    void deletingAVariantMovesItsDeclarationsOntoTheCanonicalThatSurvives() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kafak", "kafka");

        sut.delete(SLUG, "kafak");

        var order = inOrder(signalProjection, declarationRepository);
        order.verify(signalProjection).dropHiddenArea(ORG, COMMUNITY_ID, "kafak");
        order.verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "kafak", "kafka");
        order.verify(declarationRepository).removeTag(ORG, COMMUNITY_ID, "kafak");
        verify(declarationRepository, never()).removeTag(ORG, COMMUNITY_ID, "kafka");
        verify(signalProjection, never()).renameTag(eq(ORG), anyLong(), anyString(), anyString());
    }

    @Test
    void aMergedTagCannotBeDeleted() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafak", "kafka"));
        sut.merge(SLUG, "kafak", "kafka");

        assertThatThrownBy(() -> sut.delete(SLUG, "kafak")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Split");
        assertThat(artifactWriter.removals()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // an alias nobody signalled is not a verdict: the card shows no chip to split
    // -----------------------------------------------------------------------

    @Test
    void aTagIsRenameableWhenItsOnlyVariantIsOneTheCommunityNeverSignalled() {

        // given - V5 fanned the alias into a community that only ever signalled the canonical,
        // so the curation card shows "kubernetes" with no chip to split.
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "k8s", "kubernetes");

        // when
        sut.rename(SLUG, "kubernetes", "k8s");

        // then - the rename lands, and the orphan row cannot silently re-capture the new name
        assertThat(artifactWriter.renames()).containsExactly(new Rename(COMMUNITY_ID, "kubernetes", "k8s"));
        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void aTagIsDeleteableWhenTheLastSignalCarryingItsVariantIsGone() {

        // given - "kafak" was merged into "kafka", then the last artifact carrying it was retagged:
        // the alias row outlives the signal, so no chip is left to split.
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kafak", "kafka");

        // when
        sut.delete(SLUG, "kafka");

        // then
        assertThat(artifactWriter.removals()).containsExactly(new Removal(COMMUNITY_ID, "kafka"));
        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void aTagMayTakeTheNameOfAVariantTheCommunityNeverSignalled() {

        // given - "kafak" is an alias of "kafka" that no signal carries, so the curator sees the name as free
        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka", "streams"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kafak", "kafka");

        // when
        sut.rename(SLUG, "streams", "kafak");

        // then - the new name is its own tag, not silently folded into "kafka"
        assertThat(artifactWriter.renames()).containsExactly(new Rename(COMMUNITY_ID, "streams", "kafak"));
        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void anotherCommunitysAliasIsNotThisCommunitysOrphanToClear() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes"));
        tagAliasRepository.addAlias(ORG, OTHER_COMMUNITY_ID, "k8s", "kubernetes");

        sut.rename(SLUG, "kubernetes", "k8s");

        assertThat(tagAliasRepository.aliasMap(ORG, OTHER_COMMUNITY_ID)).containsEntry("k8s", "kubernetes");
    }

    // -----------------------------------------------------------------------
    // usage - what the tag is holding up
    // -----------------------------------------------------------------------

    @Test
    void usageSumsWhatEveryContextReports() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));
        artifactWriter.report(COMMUNITY_ID, "kafka", new TagUsage(TagUsageKind.ARTIFACT, 3));
        eventsWriter.report(COMMUNITY_ID, "kafka", new TagUsage(TagUsageKind.TOPIC, 2));

        assertThat(sut.usage(SLUG, "kafka")).isEqualTo(new TagUsageReport(3, 2, 0, 0, 0));
    }

    @Test
    void usageIsAllZeroesWhenNothingCarriesTheTag() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));

        assertThat(sut.usage(SLUG, "kafka")).isEqualTo(new TagUsageReport(0, 0, 0, 0, 0));
    }

    @Test
    void usageCountsThePeopleWhoDeclaredEverySpellingADeleteWouldRemove() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kafka"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "kafak", "kafka");
        when(declarationRepository.countIn(ORG, COMMUNITY_ID, Set.of("kafka", "kafak"))).thenReturn(4);

        assertThat(sut.usage(SLUG, "kafka")).isEqualTo(new TagUsageReport(0, 0, 0, 4, 0));
    }

    @Test
    void usageIsOpenOnATagOnlyDeclarationsCarry() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(declarationRepository.tagsIn(ORG, COMMUNITY_ID)).thenReturn(List.of("helm"));
        when(declarationRepository.countIn(ORG, COMMUNITY_ID, Set.of("helm"))).thenReturn(1);

        assertThat(sut.usage(SLUG, "helm")).isEqualTo(new TagUsageReport(0, 0, 0, 1, 0));
    }

    @Test
    void usageNamesTheLearningIntentsADeleteRemoves() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        eventsWriter.carry(COMMUNITY_ID, "helm");
        eventsWriter.report(COMMUNITY_ID, "helm", new TagUsage(TagUsageKind.LEARNING_INTENT, 3));

        assertThat(sut.usage(SLUG, "helm")).isEqualTo(new TagUsageReport(0, 0, 0, 0, 3));
    }

    // -----------------------------------------------------------------------
    // vocabulary - a declared tag is curated like any other
    // -----------------------------------------------------------------------

    @Test
    void theVocabularyHoldsANameOnlyAnotherContextCarries() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        eventsWriter.carry(COMMUNITY_ID, "helm");

        assertThat(sut.vocabulary(SLUG)).extracting(ExpertiseArea::canonicalTag).containsExactly("helm");
    }

    @Test
    void aTagOnlyDeclarationsCarry_canBeMergedIntoASignalledOne() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes"));
        when(declarationRepository.tagsIn(ORG, COMMUNITY_ID)).thenReturn(List.of("helm"));

        sut.merge(SLUG, "helm", "kubernetes");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).containsEntry("helm", "kubernetes");
    }

    @Test
    void aTagOnlyDeclarationsCarry_canBeRenamed() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(declarationRepository.tagsIn(ORG, COMMUNITY_ID)).thenReturn(List.of("helm"));

        sut.rename(SLUG, "helm", "helm-charts");

        assertThat(artifactWriter.renames()).containsExactly(new Rename(COMMUNITY_ID, "helm", "helm-charts"));
        verify(declarationRepository).renameTag(ORG, COMMUNITY_ID, "helm", "helm-charts");
    }

    @Test
    void aTagOnlyDeclarationsCarry_canBeDeleted() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(declarationRepository.tagsIn(ORG, COMMUNITY_ID)).thenReturn(List.of("helm"));

        sut.delete(SLUG, "helm");

        assertThat(artifactWriter.removals()).containsExactly(new Removal(COMMUNITY_ID, "helm"));
        verify(declarationRepository).removeTag(ORG, COMMUNITY_ID, "helm");
    }

    @Test
    void aMergeWhoseVariantOnlyADeclarationCarries_staysSplittable() {

        givenLeaderOf(SLUG, COMMUNITY_ID);
        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of("kubernetes"));
        when(declarationRepository.tagsIn(ORG, COMMUNITY_ID)).thenReturn(List.of("helm"));
        tagAliasRepository.addAlias(ORG, COMMUNITY_ID, "helm", "kubernetes");

        // the alias is live, so the canonical is not standalone and the delete must say so
        assertThatThrownBy(() -> sut.delete(SLUG, "kubernetes")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Split");

        sut.split(SLUG, "helm");

        assertThat(tagAliasRepository.aliasMap(ORG, COMMUNITY_ID)).isEmpty();
    }

    @Test
    void curationVerbsAreClosedToSomeoneWithoutTheGrant() {

        when(currentUser.userId()).thenReturn(NON_LEADER);
        when(communityAccess.canCurateTags(COMMUNITY_ID, NON_LEADER)).thenReturn(false);

        assertThatThrownBy(() -> sut.rename(SLUG, "kafak", "kafka")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> sut.delete(SLUG, "misc")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> sut.usage(SLUG, "misc")).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(signalProjection);
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private void givenLeaderOf(String slug, long communityId) {

        when(currentUser.userId()).thenReturn(LEADER);
        when(communityAccess.communityIdForSlug(slug)).thenReturn(communityId);
        when(communityAccess.canCurateTags(communityId, LEADER)).thenReturn(true);
    }
}
