package dev.axonia.expertise.domain;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.expertise.application.CurationScope;
import dev.axonia.expertise.application.ExpertiseCurationService;
import dev.axonia.expertise.application.NamedAreaCurationService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** A named area stores canonicals only, whatever the tag curation verbs do to the spellings around it. */
@MockitoSettings(strictness = Strictness.LENIENT)
class NamedAreaMembershipAcrossTagCurationTest {

    private static final String ORG = "org-1";
    private static final String SLUG = "eng";
    private static final long COMMUNITY_ID = 1L;
    private static final String LEADER = "alice";

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

    private FakeNamedAreaRepository namedAreaRepository;

    private ExpertiseCurationService tagCuration;
    private NamedAreaCurationService namedAreaCuration;

    @BeforeEach
    void setUp() {

        var tagAliasRepository = new FakeTagAliasRepository();
        namedAreaRepository = new FakeNamedAreaRepository();
        var curationScope = new CurationScope(currentUser, communityAccess, expertiseQueryRepository,
                declarationRepository, tagAliasRepository, List.of());
        tagCuration = new ExpertiseCurationService(organizationContext, curationScope, expertiseQueryRepository,
                tagAliasRepository, new FakeTagDismissalRepository(), List.of(), signalProjection,
                declarationRepository, namedAreaRepository, vocabularyLock);
        namedAreaCuration = new NamedAreaCurationService(organizationContext, curationScope, tagAliasRepository,
                namedAreaRepository, vocabularyLock);

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(LEADER);
        when(communityAccess.communityIdForSlug(SLUG)).thenReturn(COMMUNITY_ID);
        when(communityAccess.canCurateTags(COMMUNITY_ID, LEADER)).thenReturn(true);
        givenTheCommunityCarries("kafak", "kafka", "kafkaa");
    }

    @Test
    void mergingAPlacedVariantMovesItsPlacementOntoTheCanonical() {

        var messaging = namedAreaCuration.createNamedArea(SLUG, "Messaging").id();
        namedAreaCuration.placeInNamedArea(SLUG, messaging, "kafak");

        tagCuration.merge(SLUG, "kafak", "kafka");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID))
                .containsExactly(new NamedArea(messaging, "Messaging", List.of("kafka")));
    }

    @Test
    void mergingAPlacedVariantIntoAPlacedCanonicalKeepsTheCanonicalsPlacement() {

        var messaging = namedAreaCuration.createNamedArea(SLUG, "Messaging").id();
        var streaming = namedAreaCuration.createNamedArea(SLUG, "Streaming").id();
        namedAreaCuration.placeInNamedArea(SLUG, messaging, "kafak");
        namedAreaCuration.placeInNamedArea(SLUG, streaming, "kafka");

        tagCuration.merge(SLUG, "kafak", "kafka");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).containsExactly(
                new NamedArea(messaging, "Messaging", List.of()),
                new NamedArea(streaming, "Streaming", List.of("kafka")));
    }

    @Test
    void mergingSeveralPlacedVariantsKeepsTheFirstVariantsPlacement() {

        var messaging = namedAreaCuration.createNamedArea(SLUG, "Messaging").id();
        var streaming = namedAreaCuration.createNamedArea(SLUG, "Streaming").id();
        namedAreaCuration.placeInNamedArea(SLUG, messaging, "kafak");
        namedAreaCuration.placeInNamedArea(SLUG, streaming, "kafkaa");

        tagCuration.mergeMany(SLUG, List.of("kafak", "kafkaa"), "kafka");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).containsExactly(
                new NamedArea(messaging, "Messaging", List.of("kafka")),
                new NamedArea(streaming, "Streaming", List.of()));
    }

    @Test
    void takingOutTheCanonicalAfterAMergeRemovesOnlyItsOwnPlacement() {

        // given - "kafak" was placed, then merged into "kafka", which was then placed elsewhere
        var messaging = namedAreaCuration.createNamedArea(SLUG, "Messaging").id();
        var streaming = namedAreaCuration.createNamedArea(SLUG, "Streaming").id();
        namedAreaCuration.placeInNamedArea(SLUG, messaging, "kafak");
        tagCuration.merge(SLUG, "kafak", "kafka");
        namedAreaCuration.placeInNamedArea(SLUG, streaming, "kafka");
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).containsExactly(
                new NamedArea(messaging, "Messaging", List.of()),
                new NamedArea(streaming, "Streaming", List.of("kafka")));

        // when
        namedAreaCuration.removeFromNamedArea(SLUG, "kafka");

        // then
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID)).containsExactly(
                new NamedArea(messaging, "Messaging", List.of()), new NamedArea(streaming, "Streaming", List.of()));
    }

    @Test
    void renamingACanonicalCarriesAPlacementItsMergedVariantBrought() {

        // given - "kafak" was placed, then merged into "kafka", and no content carries "kafak" any more
        var messaging = namedAreaCuration.createNamedArea(SLUG, "Messaging").id();
        namedAreaCuration.placeInNamedArea(SLUG, messaging, "kafak");
        tagCuration.merge(SLUG, "kafak", "kafka");
        givenTheCommunityCarries("kafka");

        // when
        tagCuration.rename(SLUG, "kafka", "event-streaming");

        // then
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID))
                .containsExactly(new NamedArea(messaging, "Messaging", List.of("event-streaming")));
    }

    @Test
    void deletingAMergedVariantKeepsThePlacementItBroughtToTheCanonical() {

        // given - "kafak" was placed, then merged into "kafka", and no content carries "kafak" any more
        var messaging = namedAreaCuration.createNamedArea(SLUG, "Messaging").id();
        namedAreaCuration.placeInNamedArea(SLUG, messaging, "kafak");
        tagCuration.merge(SLUG, "kafak", "kafka");
        givenTheCommunityCarries("kafka");

        // when
        tagCuration.delete(SLUG, "kafak");

        // then
        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID))
                .containsExactly(new NamedArea(messaging, "Messaging", List.of("kafka")));
    }

    @Test
    void splittingAVariantLeavesThePlacementWithTheCanonical() {

        var messaging = namedAreaCuration.createNamedArea(SLUG, "Messaging").id();
        namedAreaCuration.placeInNamedArea(SLUG, messaging, "kafak");
        tagCuration.merge(SLUG, "kafak", "kafka");

        tagCuration.split(SLUG, "kafak");

        assertThat(namedAreaRepository.findAll(ORG, COMMUNITY_ID))
                .containsExactly(new NamedArea(messaging, "Messaging", List.of("kafka")));
    }

    private void givenTheCommunityCarries(String... tags) {

        when(expertiseQueryRepository.allTags(ORG, COMMUNITY_ID)).thenReturn(List.of(tags));
    }
}
