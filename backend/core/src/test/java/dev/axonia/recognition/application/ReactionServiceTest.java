package dev.axonia.recognition.application;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.axonia.recognition.api.ContributionSource;
import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.api.spi.ArtifactSourceAccess;
import dev.axonia.recognition.api.spi.SlotSourceAccess;
import dev.axonia.recognition.application.port.out.CommunityAccess;
import dev.axonia.recognition.application.port.out.CurrentUser;
import dev.axonia.recognition.application.port.out.OrganizationContext;
import dev.axonia.recognition.application.port.out.ReactionStore;
import dev.axonia.recognition.domain.ReactionDefinition;
import dev.axonia.recognition.domain.ReactionKind;
import dev.axonia.recognition.domain.ReactionPlacement;
import dev.axonia.recognition.domain.ReactionPlacementKey;
import dev.axonia.recognition.domain.ReactionRef;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReactionServiceTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";

    private ReactionStore reactions;
    private ReactionCatalogService catalog;
    private CommunityAccess communityAccess;
    private SlotSourceAccess slotSourceAccess;
    private ArtifactSourceAccess artifactSourceAccess;
    private ReactionService service;

    @BeforeEach
    void setUp() {
        reactions = mock(ReactionStore.class);
        catalog = mock(ReactionCatalogService.class);
        communityAccess = mock(CommunityAccess.class);
        slotSourceAccess = mock(SlotSourceAccess.class);
        artifactSourceAccess = mock(ArtifactSourceAccess.class);

        var organizationContext = mock(OrganizationContext.class);
        var currentUser = mock(CurrentUser.class);
        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(USER);
        when(reactions.place(any())).thenReturn(true);
        when(communityAccess.isMember(anyLong(), any())).thenReturn(true);
        when(slotSourceAccess.slotContribution(any(), any())).thenReturn(
                Optional.of(ContributionSource.builder().communityId(7L).contributorUserIds(List.of("someone-else"))
                        .title("A talk").occurredAt(Instant.EPOCH).build()));

        service = new ReactionService(reactions, catalog, communityAccess, slotSourceAccess, artifactSourceAccess,
                currentUser, organizationContext);
    }

    /**
     * The rule the whole safety argument rests on: a reaction never becomes a love. This cannot be proven by mocking a
     * {@code RecognizedContributionRepository} and asserting it is never called - {@link ReactionService}'s constructor
     * never takes one, so an unwired mock could never be called regardless of what the code does, and the assertion
     * would pass even if appreciation were wired in tomorrow. Reflecting over the service's own fields is the only
     * check that would actually fail the day somebody adds that wiring back.
     *
     * <p>
     * The set is closed rather than a denylist of suspicious words. A denylist over type names would miss every way
     * this regression actually arrives: a {@code RecognitionService} (which itself holds the recognized-contribution
     * repository and the event publisher), an {@code ApplicationEventPublisher}, any expertise or health port named
     * after something else. This test is the proof the separation holds, so it has to fail on any new collaborator
     * whatsoever, not on the three a denylist happened to anticipate.
     *
     * <p>
     * Synthetic fields are skipped: the coverage agent adds its own to every instrumented class, and it is not a
     * collaborator anybody wrote.
     */
    @Test
    void reactingWiresNoPathToAppreciation() {

        var collaborators = Arrays.stream(ReactionService.class.getDeclaredFields()).filter(f -> !f.isSynthetic())
                .map(f -> f.getType().getSimpleName()).toList();

        assertThat(collaborators).as("""
                ReactionService's collaborators are a closed set, and this list is the whole of it.

                A reaction is not a love and never becomes one: \
                no recognized_contribution, no credit, no domain event, no health aggregate, no expertise tier, \
                never on My impact. Every one of those arrives as a new field on this class, so a new field is \
                exactly what this test exists to stop.

                If you are adding one, say here why it cannot reach appreciation - and if it can, you are \
                superseding that rule, not extending it.""").containsExactlyInAnyOrder("ReactionStore",
                "ReactionCatalogService", "CommunityAccess", "SlotSourceAccess", "ArtifactSourceAccess", "CurrentUser",
                "OrganizationContext");
    }

    @Test
    void aMemberMayReactToTheirOwnContribution() {

        when(slotSourceAccess.slotContribution(any(), any())).thenReturn(Optional.of(ContributionSource.builder()
                .communityId(7L).contributorUserIds(List.of(USER)).title("Mine").occurredAt(Instant.EPOCH).build()));

        assertThat(
                service.react(new ReactionRequest(ContributionType.SLOT_PRESENTED, 1L, ReactionKind.EMOJI, "🧠", null)))
                .isNotNull();
    }

    @Test
    void aNonMemberCannotReact() {

        when(communityAccess.isMember(anyLong(), any())).thenReturn(false);

        assertThatThrownBy(() -> service
                .react(new ReactionRequest(ContributionType.SLOT_PRESENTED, 1L, ReactionKind.EMOJI, "🧠", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aPendingCustomReactionCannotBePlaced() {

        when(catalog.approved()).thenReturn(List.of());

        assertThatThrownBy(() -> service
                .react(new ReactionRequest(ContributionType.SLOT_PRESENTED, 1L, ReactionKind.CUSTOM, null, 99L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anApprovedCustomReactionCanBePlaced() {

        when(catalog.approved()).thenReturn(List.of(ReactionDefinition.builder().id(42L).name("ship-it")
                .imageUrl("https://cdn/ship.png").pending(false).build()));

        var tally = service
                .react(new ReactionRequest(ContributionType.SLOT_PRESENTED, 1L, ReactionKind.CUSTOM, null, 42L));

        assertThat(tally.name()).isEqualTo("ship-it");
        assertThat(tally.imageUrl()).isEqualTo("https://cdn/ship.png");
    }

    @Test
    void placingTheSameReactionTwiceIsRejected() {

        when(reactions.place(any())).thenReturn(false);

        assertThatThrownBy(() -> service
                .react(new ReactionRequest(ContributionType.SLOT_PRESENTED, 1L, ReactionKind.EMOJI, "🧠", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void reactingToAnArtifactResolvesThroughArtifactSourceAccess() {

        when(artifactSourceAccess.artifactSource(any(), any())).thenReturn(
                Optional.of(ContributionSource.builder().communityId(7L).contributorUserIds(List.of("someone-else"))
                        .title("An artifact").occurredAt(Instant.EPOCH).build()));

        assertThat(service
                .react(new ReactionRequest(ContributionType.ARTIFACT_CAPTURED, 1L, ReactionKind.EMOJI, "🧠", null)))
                .isNotNull();
    }

    @Test
    void reactingToAMissingContributionFails() {

        when(slotSourceAccess.slotContribution(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service
                .react(new ReactionRequest(ContributionType.SLOT_PRESENTED, 1L, ReactionKind.EMOJI, "🧠", null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void unreactingRemovesThePlacement() {

        when(reactions.remove(placementKey("🧠", null))).thenReturn(1);

        service.unreact(ContributionType.SLOT_PRESENTED, 1L, new ReactionRef(ReactionKind.EMOJI, "🧠", null));
    }

    @Test
    void unreactingSomethingNotThereFails() {

        when(reactions.remove(placementKey("🧠", null))).thenReturn(0);

        assertThatThrownBy(() -> service.unreact(ContributionType.SLOT_PRESENTED, 1L,
                new ReactionRef(ReactionKind.EMOJI, "🧠", null))).isInstanceOf(NotFoundException.class);
    }

    private static ReactionPlacementKey placementKey(String emoji, Long definitionId) {

        return ReactionPlacementKey.builder().organizationId(ORG).contributionType(ContributionType.SLOT_PRESENTED)
                .contributionId(1L).reactorUserId(USER).emoji(emoji).definitionId(definitionId).build();
    }

    @Test
    void theTallyMarksTheViewersOwn() {

        when(reactions.placementsOn(ORG, ContributionType.SLOT_PRESENTED, 1L))
                .thenReturn(List.of(placement("🧠", USER), placement("🧠", "other"), placement("🎯", "other")));

        var tally = service.tally(ContributionType.SLOT_PRESENTED, 1L);

        assertThat(tally).extracting(ReactionTally::emoji, ReactionTally::count, ReactionTally::viewerHasReacted)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("🧠", 2, true),
                        org.assertj.core.groups.Tuple.tuple("🎯", 1, false));
    }

    @Test
    void theTallyResolvesACustomReactionsNameAndImage() {

        when(catalog.approved()).thenReturn(List.of(ReactionDefinition.builder().id(42L).name("ship-it")
                .imageUrl("https://cdn/ship.png").pending(false).build()));
        when(reactions.placementsOn(ORG, ContributionType.SLOT_PRESENTED, 1L))
                .thenReturn(List.of(customPlacement(42L, USER)));

        var tally = service.tally(ContributionType.SLOT_PRESENTED, 1L);

        assertThat(tally).hasSize(1);
        assertThat(tally.get(0).name()).isEqualTo("ship-it");
        assertThat(tally.get(0).imageUrl()).isEqualTo("https://cdn/ship.png");
    }

    /**
     * The strip renders once per slot and once per artifact card, and most of them carry only standard emoji. Reading
     * the Organization's approved vocabulary to resolve nothing is a cost a session page would pay many times over.
     */
    @Test
    void anEmojiOnlyContributionNeverReadsTheCatalog() {

        when(reactions.placementsOn(ORG, ContributionType.SLOT_PRESENTED, 1L))
                .thenReturn(List.of(placement("🧠", USER), placement("🎯", "other")));

        var tally = service.tally(ContributionType.SLOT_PRESENTED, 1L);

        assertThat(tally).hasSize(2);
        verify(catalog, never()).approved();
    }

    /** An empty contribution is the same read again with nothing in it, and must not reach for the catalog either. */
    @Test
    void aContributionWithNoReactionsNeverReadsTheCatalog() {

        when(reactions.placementsOn(ORG, ContributionType.SLOT_PRESENTED, 1L)).thenReturn(List.of());

        assertThat(service.tally(ContributionType.SLOT_PRESENTED, 1L)).isEmpty();
        verify(catalog, never()).approved();
    }

    /** One custom placement is enough to pay for the catalog: the strip cannot draw it without a name and an image. */
    @Test
    void oneCustomPlacementAmongEmojiStillReadsTheCatalog() {

        when(catalog.approved()).thenReturn(List.of(ReactionDefinition.builder().id(42L).name("ship-it")
                .imageUrl("https://cdn/ship.png").pending(false).build()));
        when(reactions.placementsOn(ORG, ContributionType.SLOT_PRESENTED, 1L))
                .thenReturn(List.of(placement("🧠", USER), customPlacement(42L, "other")));

        var tally = service.tally(ContributionType.SLOT_PRESENTED, 1L);

        assertThat(tally).extracting(ReactionTally::name).containsExactlyInAnyOrder(null, "ship-it");
        verify(catalog).approved();
    }

    /** Two reactions tied on count keep the strip stable across renders: earliest placement sorts first. */
    @Test
    void tiedReactionsAreOrderedByFirstPlacement() {

        var earlier = Instant.EPOCH.plusSeconds(1);
        var later = Instant.EPOCH.plusSeconds(2);
        when(reactions.placementsOn(ORG, ContributionType.SLOT_PRESENTED, 1L))
                .thenReturn(List.of(placementAt("🎯", "other", later), placementAt("🧠", USER, earlier)));

        var tally = service.tally(ContributionType.SLOT_PRESENTED, 1L);

        assertThat(tally).extracting(ReactionTally::emoji).containsExactly("🧠", "🎯");
    }

    private static ReactionPlacement placement(String emoji, String userId) {

        return ReactionPlacement.builder().organizationId(ORG).communityId(7L)
                .contributionType(ContributionType.SLOT_PRESENTED).contributionId(1L).reactorUserId(userId).emoji(emoji)
                .build();
    }

    private static ReactionPlacement placementAt(String emoji, String userId, Instant createdAt) {

        return placement(emoji, userId).toBuilder().createdAt(createdAt).build();
    }

    private static ReactionPlacement customPlacement(Long definitionId, String userId) {

        return ReactionPlacement.builder().organizationId(ORG).communityId(7L)
                .contributionType(ContributionType.SLOT_PRESENTED).contributionId(1L).reactorUserId(userId)
                .definitionId(definitionId).build();
    }
}
