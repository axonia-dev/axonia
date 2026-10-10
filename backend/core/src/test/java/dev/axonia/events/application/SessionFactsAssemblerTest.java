package dev.axonia.events.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.CommunityIdentity;
import dev.axonia.events.adapter.out.communication.AwaitingCloseProducerTest;
import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.api.MeetingPlace;
import dev.axonia.events.api.ResourceLink;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionMother;
import dev.axonia.events.api.SessionResources;
import dev.axonia.events.api.spi.SessionResourceAccess;
import dev.axonia.events.application.port.out.ActionItemCheckpointStore;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.application.port.out.UserProfileAccess;
import dev.axonia.events.domain.ActionItemCheckpointRecord;
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.CheckpointOutcome;
import dev.axonia.events.domain.SeriesRecord;
import dev.axonia.events.domain.SessionFacts;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.shared.api.web.PublicUrls;
import dev.axonia.shared.configuration.PublicUrlProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionFactsAssemblerTest {

    private static final String ORGANIZATION_ID = "acme";
    private static final Long COMMUNITY_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-08-14T18:00:00Z");
    private static final String BOB = "user-bob";

    @Mock
    private SessionMapper sessionMapper;

    @Mock
    private SeriesStore seriesStore;

    @Mock
    private SessionStore sessionStore;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private UserProfileAccess userProfileAccess;

    @Mock
    private SessionResourceAccess sessionResourceAccess;

    @Mock
    private ActionItemStore actionItemStore;

    @Mock
    private ActionItemCheckpointStore actionItemCheckpointStore;

    private final PublicUrls publicUrlProperties = new PublicUrlProperties("https://axonia.example");

    private SessionFactsAssembler assembler;

    @BeforeEach
    void buildAssembler() {

        // Built by hand, not @InjectMocks: publicUrlProperties is a plain field, not a @Mock, and @InjectMocks'
        // constructor strategy only fills parameters it can match to a mock - it would otherwise pass null.
        assembler = new SessionFactsAssembler(sessionMapper, seriesStore, sessionStore, communityAccess,
                userProfileAccess, sessionResourceAccess, actionItemStore, actionItemCheckpointStore,
                publicUrlProperties);
    }

    @Test
    void facts_assemblesTheRealSessionUrlFromTheCommunitySlugAndThePublicBaseUrl() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        when(communityAccess.communityIdentity(ORGANIZATION_ID, COMMUNITY_ID))
                .thenReturn(new CommunityIdentity("frontend-guild", "Frontend Guild", "#1aa6a0"));
        when(userProfileAccess.displayNames(ORGANIZATION_ID, List.of("dev-user"))).thenReturn(Map.of());
        when(sessionResourceAccess.resourcesForSession(ORGANIZATION_ID, 42L, COMMUNITY_ID))
                .thenReturn(new SessionResources(List.of(), Map.of()));

        // when
        var facts = assembler.facts(ORGANIZATION_ID, entity);

        // then
        assertThat(facts).containsEntry("sessionUrl", "https://axonia.example/communities/frontend-guild/sessions/42");
    }

    /**
     * One lookup, two uses. The identity is what the session URL's slug comes from AND what the mail's community
     * monogram comes from, so resolving it twice would double a query for no gain.
     */
    @Test
    @SuppressWarnings("unchecked")
    void theCommunityIsResolvedOnceAndFeedsBothTheUrlAndTheFact() {

        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        givenLeafDependencies(entity);

        var facts = assembler.facts(ORGANIZATION_ID, entity);

        assertThat(facts).containsEntry("sessionUrl", "https://axonia.example/communities/frontend-guild/sessions/42");
        assertThat((Map<String, String>) facts.get("sender")).containsEntry("name", "Frontend Guild");
        verify(communityAccess, times(1)).communityIdentity(ORGANIZATION_ID, COMMUNITY_ID);
    }

    /**
     * {@code SessionFactsContext} carries a slot's resources as full {@link ResourceLink}s, not bare URLs
     * ({@link SessionResourceAccess} answers with both halves, and the slot half is not narrowed down to a URL before
     * {@link SessionFacts} sees it); this proves both a link's title and its URL survive into the fact map, keyed by
     * the same slot id {@link SessionResources#bySlot} used.
     */
    @Test
    void facts_carriesBySlotResourceLinksThroughAsTitleAndUrlPairsKeyedBySameSlotId() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        when(communityAccess.communityIdentity(ORGANIZATION_ID, COMMUNITY_ID))
                .thenReturn(new CommunityIdentity("frontend-guild", "Frontend Guild", "#1aa6a0"));
        when(userProfileAccess.displayNames(ORGANIZATION_ID, List.of("dev-user"))).thenReturn(Map.of());
        when(sessionResourceAccess.resourcesForSession(ORGANIZATION_ID, 42L, COMMUNITY_ID))
                .thenReturn(new SessionResources(List.of(),
                        Map.of(11L, List.of(new ResourceLink("Slides", "https://example.com/deck.pdf")))));

        // when
        var facts = assembler.facts(ORGANIZATION_ID, entity);

        // then
        @SuppressWarnings("unchecked")
        var talks = (List<Map<String, Object>>) facts.get("talks");
        assertThat(talks).singleElement().extracting(talk -> talk.get("resources"))
                .isEqualTo(List.of(Map.of("title", "Slides", "url", "https://example.com/deck.pdf")));
    }

    /**
     * {@link SessionResources#sessionLevel} - resources attached to the session itself, not to any one slot - now
     * reaches the top-level {@code resources} fact instead of being resolved and set aside.
     */
    @Test
    void facts_carriesSessionLevelResourceLinksToTheTopLevelResourcesFact() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        givenLeafDependencies(entity);
        when(sessionResourceAccess.resourcesForSession(ORGANIZATION_ID, 42L, COMMUNITY_ID)).thenReturn(
                new SessionResources(List.of(new ResourceLink("Agenda", "https://example.com/agenda.pdf")), Map.of()));

        // when
        var facts = assembler.facts(ORGANIZATION_ID, entity);

        // then
        assertThat(facts.get("resources"))
                .isEqualTo(List.of(Map.of("title", "Agenda", "url", "https://example.com/agenda.pdf")));
    }

    /**
     * Action points and checked items are a wrap-up-only concern: resolving and persisting them into a
     * {@code SESSION_UPCOMING} or awaiting-close row that never renders them would be wasted work and a needless
     * widening of what personal data that row carries. {@code facts()} - the overload those two message types use -
     * must never touch {@link ActionItemStore} or {@link ActionItemCheckpointStore} at all, and must never leak an
     * {@code actionPoints} value even if some hypothetically existed for this session.
     */
    @Test
    void facts_neverResolvesActionPoints() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        givenLeafDependencies(entity);

        // when
        var facts = assembler.facts(ORGANIZATION_ID, entity);

        // then
        assertThat(facts).containsEntry("actionPoints", null);
        verifyNoInteractions(actionItemStore, actionItemCheckpointStore);
    }

    @Test
    void factsForWrapUp_carriesActionPointsWithAssigneeResolvedThroughTheSameDisplayNamesMapAsPresenters() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        when(communityAccess.communityIdentity(ORGANIZATION_ID, COMMUNITY_ID))
                .thenReturn(new CommunityIdentity("frontend-guild", "Frontend Guild", "#1aa6a0"));
        when(sessionResourceAccess.resourcesForSession(ORGANIZATION_ID, 42L, COMMUNITY_ID))
                .thenReturn(new SessionResources(List.of(), Map.of()));
        when(actionItemStore.findByOriginSessionId(ORGANIZATION_ID, COMMUNITY_ID, 42L))
                .thenReturn(List.of(anActionItem().title("Write up the outage retro").assigneeUserId("user-bob")
                        .dueDate(LocalDate.of(2026, 8, 20)).outcome(ActionItemOutcome.OPEN).build()));
        // Both the presenter and the assignee id are resolved through the same one batched call.
        when(userProfileAccess.displayNames(ORGANIZATION_ID, List.of("dev-user", "user-bob")))
                .thenReturn(Map.of("user-bob", "Bob"));

        // when
        var facts = assembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW);

        // then
        assertThat(facts.get("actionPoints")).isEqualTo(List.of(Map.of("title", "Write up the outage retro", "assignee",
                "Bob", "dueDate", LocalDate.of(2026, 8, 20), "done", false, "dropped", false)));
    }

    @Test
    void factsForWrapUp_dropsAnActionPointsAssigneeIdRatherThanLeakingItWhenUnresolvable() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        when(communityAccess.communityIdentity(ORGANIZATION_ID, COMMUNITY_ID))
                .thenReturn(new CommunityIdentity("frontend-guild", "Frontend Guild", "#1aa6a0"));
        when(sessionResourceAccess.resourcesForSession(ORGANIZATION_ID, 42L, COMMUNITY_ID))
                .thenReturn(new SessionResources(List.of(), Map.of()));
        when(actionItemStore.findByOriginSessionId(ORGANIZATION_ID, COMMUNITY_ID, 42L)).thenReturn(List.of(
                anActionItem().assigneeUserId("subject-with-no-display-name").outcome(ActionItemOutcome.DONE).build()));
        when(userProfileAccess.displayNames(ORGANIZATION_ID, List.of("dev-user", "subject-with-no-display-name")))
                .thenReturn(Map.of());

        // when
        var facts = assembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW);

        // then
        @SuppressWarnings("unchecked")
        var actionPoints = (List<Map<String, Object>>) facts.get("actionPoints");
        assertThat(actionPoints).singleElement().satisfies(point -> assertThat(point).containsEntry("assignee", null)
                .containsEntry("done", true).containsEntry("dropped", false));
    }

    /**
     * The wrap-up's two sections read from two different queries against two different concerns of the same aggregate:
     * {@code actionPoints} is what THIS session raised ({@link ActionItemStore#findByOriginSessionId}),
     * {@code checkedItems} is what THIS session answered
     * ({@link ActionItemCheckpointStore#findByOrganizationIdAndCommunityIdAndSessionId} joined against
     * {@link ActionItemStore#findByOrganizationIdAndIdIn} for the title the checkpoint log itself does not carry). One
     * item can raise here without being answered here, and one answered here can have been raised at an earlier session
     * - this proves the two lists stay independent rather than one leaking into the other.
     */
    @Test
    void factsForWrapUp_reportsWhatWasCheckedAlongsideWhatWasAgreed() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        when(communityAccess.communityIdentity(ORGANIZATION_ID, COMMUNITY_ID))
                .thenReturn(new CommunityIdentity("frontend-guild", "Frontend Guild", "#1aa6a0"));
        when(sessionResourceAccess.resourcesForSession(ORGANIZATION_ID, 42L, COMMUNITY_ID))
                .thenReturn(new SessionResources(List.of(), Map.of()));
        when(actionItemStore.findByOriginSessionId(ORGANIZATION_ID, COMMUNITY_ID, 42L))
                .thenReturn(List.of(anActionItem().id(1L).title("Close the Jira ticket").assigneeUserId(BOB)
                        .outcome(ActionItemOutcome.OPEN).build()));
        when(actionItemCheckpointStore.findByOrganizationIdAndCommunityIdAndSessionId(ORGANIZATION_ID, COMMUNITY_ID,
                42L))
                .thenReturn(List.of(ActionItemCheckpointRecord.builder().id(9L).organizationId(ORGANIZATION_ID)
                        .actionItemId(2L).sessionId(42L).outcome(CheckpointOutcome.CARRIED).decidedByUserId(BOB)
                        .decidedAt(NOW).build()));
        when(actionItemStore.findByOrganizationIdAndIdIn(ORGANIZATION_ID, List.of(2L))).thenReturn(
                List.of(anActionItem().id(2L).title("Renew the certificate").outcome(ActionItemOutcome.OPEN).build()));
        when(userProfileAccess.displayNames(ORGANIZATION_ID, List.of("dev-user", BOB))).thenReturn(Map.of(BOB, "Bob"));

        // when
        var facts = assembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW);

        // then
        @SuppressWarnings("unchecked")
        var actionPoints = (List<Map<String, Object>>) facts.get("actionPoints");
        assertThat(actionPoints).extracting(point -> point.get("title")).containsExactly("Close the Jira ticket");

        @SuppressWarnings("unchecked")
        var checkedItems = (List<Map<String, Object>>) facts.get("checkedItems");
        assertThat(checkedItems).singleElement().satisfies(item -> assertThat(item)
                .containsEntry("title", "Renew the certificate").containsEntry("outcome", "Carried"));
    }

    /**
     * Not reachable today - {@code action_item_checkpoint_item_fkey} is {@code ON DELETE CASCADE}, so a checkpoint row
     * can never outlive the item it names. Proven anyway: a missing "What was checked" line is a smaller failure than a
     * wrap-up that never sends because one row's lookup came back empty.
     */
    @Test
    void factsForWrapUp_dropsACheckedItemRatherThanFailingTheWholeMailWhenTheItemLookupMisses() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        when(communityAccess.communityIdentity(ORGANIZATION_ID, COMMUNITY_ID))
                .thenReturn(new CommunityIdentity("frontend-guild", "Frontend Guild", "#1aa6a0"));
        when(sessionResourceAccess.resourcesForSession(ORGANIZATION_ID, 42L, COMMUNITY_ID))
                .thenReturn(new SessionResources(List.of(), Map.of()));
        when(actionItemStore.findByOriginSessionId(ORGANIZATION_ID, COMMUNITY_ID, 42L)).thenReturn(List.of());
        when(actionItemCheckpointStore.findByOrganizationIdAndCommunityIdAndSessionId(ORGANIZATION_ID, COMMUNITY_ID,
                42L))
                .thenReturn(List.of(ActionItemCheckpointRecord.builder().id(9L).organizationId(ORGANIZATION_ID)
                        .actionItemId(2L).sessionId(42L).outcome(CheckpointOutcome.CARRIED).decidedByUserId(BOB)
                        .decidedAt(NOW).build()));
        when(actionItemStore.findByOrganizationIdAndIdIn(ORGANIZATION_ID, List.of(2L))).thenReturn(List.of());
        when(userProfileAccess.displayNames(ORGANIZATION_ID, List.of("dev-user"))).thenReturn(Map.of());

        // when
        var facts = assembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW);

        // then
        assertThat(facts).containsEntry("checkedItems", null);
    }

    private static ActionItemRecord.ActionItemRecordBuilder anActionItem() {

        return ActionItemRecord.builder().organizationId(ORGANIZATION_ID).communityId(COMMUNITY_ID).title("Follow up")
                .description("").raisedByUserId("alice");
    }

    @Test
    void facts_returnsNoSeriesTitleWhenTheSessionHasNoSeries() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        givenLeafDependencies(entity);

        // when
        var facts = assembler.facts(ORGANIZATION_ID, entity);

        // then
        assertThat(facts).containsEntry("seriesTitle", null);
    }

    @Test
    void facts_resolvesTheSeriesTitleWhenTheSessionHasASeries() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(9L).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(9L).build());
        givenLeafDependencies(entity);
        when(seriesStore.findByOrganizationIdAndId(ORGANIZATION_ID, 9L))
                .thenReturn(Optional.of(SeriesRecord.builder().id(9L).title("Frontend Dojo").build()));

        // when
        var facts = assembler.facts(ORGANIZATION_ID, entity);

        // then
        assertThat(facts).containsEntry("seriesTitle", "Frontend Dojo");
    }

    @Test
    void facts_neverResolvesNextSession() {

        // given - only factsForWrapUp does; facts() is used by SESSION_UPCOMING and the awaiting-close nudge,
        // neither of which reads it.
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(9L).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(9L).build());
        givenLeafDependencies(entity);
        when(seriesStore.findByOrganizationIdAndId(ORGANIZATION_ID, 9L)).thenReturn(Optional.empty());

        // when
        var facts = assembler.facts(ORGANIZATION_ID, entity);

        // then
        assertThat(facts).containsEntry("nextSession", null);
        verifyNoInteractions(sessionStore);
    }

    @Test
    void factsForWrapUp_returnsNoNextSessionWhenTheSessionHasNoSeries() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        givenLeafDependencies(entity);

        // when
        var facts = assembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW);

        // then
        assertThat(facts).containsEntry("nextSession", null);
        verifyNoInteractions(sessionStore);
    }

    @Test
    void factsForWrapUp_returnsNoNextSessionWhenNoneIsPending() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(9L).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(9L).build());
        givenLeafDependencies(entity);
        when(seriesStore.findByOrganizationIdAndId(ORGANIZATION_ID, 9L))
                .thenReturn(Optional.of(SeriesRecord.builder().id(9L).title("Frontend Dojo").build()));
        when(sessionStore.findPendingForSeriesExcluding(ORGANIZATION_ID, 9L, 42L, NOW)).thenReturn(Optional.empty());

        // when
        var facts = assembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW);

        // then
        assertThat(facts).containsEntry("nextSession", null);
    }

    /**
     * Proves the exclusion is wired end to end, not just accepted by the repository signature: the assembler passes its
     * own session id (42) as {@code excludeSessionId}, and the genuine following session (43) the query answers with -
     * never 42 itself - is what gets labeled. Before this, a client-side {@code .filter()} that excluded 42 AFTER a
     * single-row query had already committed to it would drop the whole "coming up next" line instead of looking past
     * it for 43.
     */
    @Test
    void factsForWrapUp_asksTheRepositoryToExcludeTheSessionItselfSoItFindsTheGenuineNextOne() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(9L).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(9L).build());
        givenLeafDependencies(entity);
        when(seriesStore.findByOrganizationIdAndId(ORGANIZATION_ID, 9L)).thenReturn(
                Optional.of(SeriesRecord.builder().id(9L).title("Frontend Dojo").timeZone("Europe/Brussels").build()));
        when(sessionStore.findPendingForSeriesExcluding(ORGANIZATION_ID, 9L, 42L, NOW))
                .thenReturn(Optional.of(SessionRecord.builder().id(43L).title("Structured concurrency")
                        .scheduledAt(Instant.parse("2026-08-29T18:00:00Z")).build()));

        // when
        var facts = assembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW);

        // then
        assertThat(facts).containsEntry("nextSession", "29 August - Structured concurrency");
    }

    @Test
    void factsForWrapUp_labelsTheNextSessionWithOnlyItsDateWhenItHasNoTitleYet() {

        // given - a materialised-but-not-yet-planned next occurrence still carries no title.
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(9L).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(9L).build());
        givenLeafDependencies(entity);
        when(seriesStore.findByOrganizationIdAndId(ORGANIZATION_ID, 9L)).thenReturn(
                Optional.of(SeriesRecord.builder().id(9L).title("Frontend Dojo").timeZone("Europe/Brussels").build()));
        when(sessionStore.findPendingForSeriesExcluding(ORGANIZATION_ID, 9L, 42L, NOW))
                .thenReturn(Optional.of(SessionRecord.builder().id(43L).title(null)
                        .scheduledAt(Instant.parse("2026-08-29T18:00:00Z")).build()));

        // when
        var facts = assembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW);

        // then
        assertThat(facts).containsEntry("nextSession", "29 August");
    }

    /**
     * The only case in this file that exercises {@code factsForAwaitingClose} against the real assembler rather than a
     * mock - {@link AwaitingCloseProducerTest} stubs {@code factsAssembler} itself, so its own tests never run this
     * method's real body. Without this, the token the nudge stamps at production would flow through an assembler method
     * nothing ever actually calls.
     */
    @Test
    void factsForAwaitingClose_carriesTheNudgeArmTokenTheProducerStamped() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        givenLeafDependencies(entity);

        // when
        var facts = assembler.factsForAwaitingClose(ORGANIZATION_ID, entity, "SEVEN_DAY");

        // then
        assertThat(facts).containsEntry("nudgeArm", "SEVEN_DAY");
    }

    /**
     * The place the reminder links to is the session's EFFECTIVE one - {@link SessionMapper} already resolves an
     * occurrence's own override against its series' standing place through {@link MeetingPlace}, so the fact map reads
     * the answer rather than resolving it a second time and risking a second, divergent rule.
     */
    @Test
    void carriesTheSessionsPlaceSoTheReminderCanRenderIt() {

        var facts = assembler.facts(ORGANIZATION_ID,
                sessionWithPlace(new MeetingPlace("Room 4B, Gasperich", "https://meet.example/dojo", "Passcode 4242")));

        assertThat(facts).containsEntry("location", "Room 4B, Gasperich")
                .containsEntry("joinUrl", "https://meet.example/dojo").containsEntry("joiningDetails", "Passcode 4242");
    }

    /**
     * Absent, not present-and-null: the reminder branches on each key being there at all, so a session that meets
     * nowhere carries no place fact for the template to render an empty line from.
     */
    @Test
    void omitsThePlaceEntirelyWhenNeitherSessionNorSeriesHasOne() {

        var facts = assembler.facts(ORGANIZATION_ID, sessionWithPlace(MeetingPlace.NONE));

        assertThat(facts).doesNotContainKeys("location", "joinUrl", "joiningDetails");
    }

    /**
     * Each part stands on its own key, so a session naming only where to stand carries that one and neither of the
     * others - the template's three guards are independent and must be fed independently.
     */
    @Test
    void omitsOnlyThePartsTheSessionLeftBlank() {

        var facts = assembler.facts(ORGANIZATION_ID,
                sessionWithPlace(new MeetingPlace("Room 4B, Gasperich", null, null)));

        assertThat(facts).containsEntry("location", "Room 4B, Gasperich").doesNotContainKeys("joinUrl",
                "joiningDetails");
    }

    private SessionRecord sessionWithPlace(MeetingPlace place) {

        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).place(place).build());
        givenLeafDependencies(entity);
        return entity;
    }

    /**
     * The date the community actually meets on, not the date at Greenwich. A series in Auckland whose next occurrence
     * is stamped 29 August 18:00 UTC meets on the 30th where its members are, and a wrap-up naming the 29th sends them
     * looking for a session on the wrong day.
     */
    @Test
    void factsForWrapUp_datesTheNextSessionInTheSeriesOwnZoneRatherThanAtUtc() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(9L).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(9L).build());
        givenLeafDependencies(entity);
        when(seriesStore.findByOrganizationIdAndId(ORGANIZATION_ID, 9L)).thenReturn(
                Optional.of(SeriesRecord.builder().id(9L).title("Frontend Dojo").timeZone("Pacific/Auckland").build()));
        when(sessionStore.findPendingForSeriesExcluding(ORGANIZATION_ID, 9L, 42L, NOW))
                .thenReturn(Optional.of(SessionRecord.builder().id(43L).title("Structured concurrency")
                        .scheduledAt(Instant.parse("2026-08-29T18:00:00Z")).build()));

        // when
        var facts = assembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW);

        // then
        assertThat(facts).containsEntry("nextSession", "30 August - Structured concurrency");
    }

    /**
     * A session whose series row is gone still has a next occurrence to name, and no zone to name it in. UTC is the
     * fallback rather than the server's own default, so the label never depends on where the sweep happens to run.
     */
    @Test
    void factsForWrapUp_datesTheNextSessionAtUtcWhenThereIsNoSeriesToTakeAZoneFrom() {

        // given
        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(9L).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(9L).build());
        givenLeafDependencies(entity);
        when(seriesStore.findByOrganizationIdAndId(ORGANIZATION_ID, 9L)).thenReturn(Optional.empty());
        when(sessionStore.findPendingForSeriesExcluding(ORGANIZATION_ID, 9L, 42L, NOW))
                .thenReturn(Optional.of(SessionRecord.builder().id(43L).title("Structured concurrency")
                        .scheduledAt(Instant.parse("2026-08-29T23:30:00Z")).build()));

        // when
        var facts = assembler.factsForWrapUp(ORGANIZATION_ID, entity, NOW);

        // then
        assertThat(facts).containsEntry("nextSession", "29 August - Structured concurrency");
    }

    @Test
    void theBacklogUrlIsBuiltFromTheSameSlugTheSessionUrlIs() {

        var entity = SessionRecordMother.any().id(42L).communityId(COMMUNITY_ID).seriesId(null).build();
        givenMappedSession(entity, SessionMother.any().id(42L).seriesId(null).build());
        givenLeafDependencies(entity);

        assertThat(assembler.facts(ORGANIZATION_ID, entity)).containsEntry("backlogUrl",
                "https://axonia.example/communities/frontend-guild/backlog");
    }

    private void givenMappedSession(SessionRecord entity, Session session) {

        when(sessionMapper.toSession(entity)).thenReturn(session);
    }

    private void givenLeafDependencies(SessionRecord entity) {

        when(communityAccess.communityIdentity(ORGANIZATION_ID, COMMUNITY_ID))
                .thenReturn(new CommunityIdentity("frontend-guild", "Frontend Guild", "#1aa6a0"));
        when(userProfileAccess.displayNames(ORGANIZATION_ID, List.of("dev-user"))).thenReturn(Map.of());
        when(sessionResourceAccess.resourcesForSession(ORGANIZATION_ID, entity.getId(), COMMUNITY_ID))
                .thenReturn(new SessionResources(List.of(), Map.of()));
    }
}
