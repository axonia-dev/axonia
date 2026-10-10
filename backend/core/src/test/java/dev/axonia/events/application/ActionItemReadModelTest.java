package dev.axonia.events.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.adapter.out.persistence.ActionItemCheckpointRepository;
import dev.axonia.events.adapter.out.persistence.ActionItemRepository;
import dev.axonia.events.adapter.out.persistence.JdbcActionItemCheckpointStore;
import dev.axonia.events.adapter.out.persistence.JdbcActionItemStore;
import dev.axonia.events.adapter.out.persistence.JdbcSeriesStore;
import dev.axonia.events.adapter.out.persistence.JdbcSessionStore;
import dev.axonia.events.adapter.out.persistence.SeriesRepository;
import dev.axonia.events.adapter.out.persistence.SessionRepository;
import dev.axonia.events.api.ActionItemOutcome;
import dev.axonia.events.application.port.out.ActionItemCheckpointStore;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.application.port.out.UserProfileAccess;
import dev.axonia.events.domain.ActionItemCheckpointRecord;
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.ActionItemTag;
import dev.axonia.events.domain.CheckpointOutcome;
import dev.axonia.events.domain.SeriesRecordMother;
import dev.axonia.events.domain.SessionRecordMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@code CheckpointResolverTest} already pins the resolver's own rules against mocks; this suite is the one place those
 * rules are proven against a real database, through the read model a controller actually calls, and the one place the
 * carry count and the list read's batching are proven at all.
 */
@ExtendWith(MockitoExtension.class)
class ActionItemReadModelTest extends CoreJdbcTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-19T09:00:00Z");
    private static final long COMMUNITY = 1L;
    private static final long COMMUNITY_WITH_NO_SESSIONS = 2L;
    private static final String ASSIGNEE = "carol";

    private ActionItemStore actionItemStore;

    private ActionItemCheckpointStore actionItemCheckpointStore;

    private SessionStore sessionStore;

    private SeriesStore seriesStore;

    @Autowired
    void wireStores(ActionItemRepository actionItemRepository,
            ActionItemCheckpointRepository actionItemCheckpointRepository, SessionRepository sessionRepository,
            SeriesRepository seriesRepository) {

        actionItemStore = new JdbcActionItemStore(actionItemRepository);
        actionItemCheckpointStore = new JdbcActionItemCheckpointStore(actionItemCheckpointRepository);
        sessionStore = new JdbcSessionStore(sessionRepository);
        seriesStore = new JdbcSeriesStore(seriesRepository);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Mock
    private UserProfileAccess userProfileAccess;

    @Mock
    private TopicAuthorizer topicAuthorizer;

    @Mock
    private CurrentUser currentUser;

    private ActionItemReadModel readModel;

    @Mock
    private SessionStore mockSessionStore;

    @Mock
    private SeriesStore mockSeriesStore;

    @BeforeEach
    void buildReadModel() {

        var checkpointResolver = new CheckpointResolver(sessionStore, seriesStore, Clock.fixed(NOW, ZoneOffset.UTC));
        var actionItemMapper = new ActionItemMapper();
        readModel = new ActionItemReadModel(actionItemCheckpointStore, checkpointResolver, userProfileAccess,
                topicAuthorizer, actionItemMapper, currentUser, actionItemStore);
    }

    @BeforeEach
    void seedSeams() {

        given(currentUser.userId()).willReturn("alice");
        lenient().when(userProfileAccess.displayName(any(), any())).thenReturn("Alice");
        lenient().when(userProfileAccess.displayNames(any(), any())).thenReturn(Map.of());
        lenient().when(topicAuthorizer.canManageInCommunity(anyLong(), any())).thenReturn(true);
    }

    @BeforeEach
    void seedCommunities() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING");
        for (var id : List.of(COMMUNITY, COMMUNITY_WITH_NO_SESSIONS)) {
            jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                    + "created_at, updated_at, version) SELECT ?,'default','c' || ?,'C', k.id,'','ACTIVE', now(), "
                    + "now(), 0 FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' "
                    + "ON CONFLICT DO NOTHING", id, id);
        }
    }

    @Test
    void anUnpinnedItemReadsAsTheCommunitysNextSession() {

        var next = seedUpcomingSession(COMMUNITY, Instant.parse("2026-09-24T09:00:00Z"));
        var item = seedActionItem(COMMUNITY, null);

        var read = readModel.single(item);

        assertThat(read.checkpoint().sessionId()).isEqualTo(next);
        assertThat(read.checkpoint().pinned()).isFalse();
    }

    @Test
    void anUnpinnedItemInACommunityWithNoUpcomingSessionStillOwesOne() {

        var item = seedActionItem(COMMUNITY_WITH_NO_SESSIONS, null);

        var read = readModel.single(item);

        assertThat(read.checkpoint().sessionId()).isNull();
        assertThat(read.checkpoint().pinned()).isFalse();
    }

    @Test
    void aPinnedItemReadsItsOwnSessionEvenWhenAnEarlierOneExists() {

        seedUpcomingSession(COMMUNITY, Instant.parse("2026-09-24T09:00:00Z"));
        var later = seedUpcomingSession(COMMUNITY, Instant.parse("2026-10-08T09:00:00Z"));
        var item = seedActionItem(COMMUNITY, later);

        var read = readModel.single(item);

        assertThat(read.checkpoint().sessionId()).isEqualTo(later);
        assertThat(read.checkpoint().pinned()).isTrue();
    }

    @Test
    void carryCountIsTheNumberOfCarriedRows() {

        var checkpointSession = seedUpcomingSession(COMMUNITY, Instant.parse("2026-09-24T09:00:00Z"));
        var item = seedActionItem(COMMUNITY, null);
        seedCheckpointRow(item.getId(), checkpointSession, CheckpointOutcome.CARRIED);
        seedCheckpointRow(item.getId(), checkpointSession, CheckpointOutcome.CARRIED);

        assertThat(readModel.single(item).carryCount()).isEqualTo(2);
    }

    /**
     * Values only - the query-count claim ("no more statements per row") lives in
     * {@code ReadPathQueryBudgetTest.aPageOfActionItemsCostsAFixedHandfulOfQueriesAndDoesNotGrowWithPageSize}, a
     * {@code @SpringBootTest} that already counts statements and costs no extra Spring context to do it. This suite
     * stays on the one plain {@code @DataJdbcTest} context every other test here shares: importing
     * {@code CountingDataSourceRegistrar} here as well would give it a second, distinct context against the same shared
     * Testcontainers Postgres pool - the exact shape that has previously produced "too many clients already" under the
     * full gate, deterministically, not as a flake.
     */
    @Test
    void listCarriesTheRightCountsAndCheckpointsForEveryRowOnThePage() {

        var pinned = seedUpcomingSession(COMMUNITY, Instant.parse("2026-10-08T09:00:00Z"));
        var soonest = seedUpcomingSession(COMMUNITY, Instant.parse("2026-09-24T09:00:00Z"));
        var pinnedItem = actionItemStore.save(seedActionItemBuilder(COMMUNITY, pinned)
                .tags(Set.of(ActionItemTag.builder().tag("kafka").build())).build());
        var unpinnedItem = actionItemStore.save(
                seedActionItemBuilder(COMMUNITY, null).tags(Set.of(ActionItemTag.builder().tag("ci").build())).build());
        var assignedItem = actionItemStore.save(seedActionItemBuilder(COMMUNITY, pinned).assigneeUserId(ASSIGNEE)
                .tags(Set.of(ActionItemTag.builder().tag("infra").build())).build());
        seedCheckpointRow(unpinnedItem.getId(), soonest, CheckpointOutcome.CARRIED);
        lenient().when(userProfileAccess.displayNames(eq("default"), any()))
                .thenReturn(Map.of(ASSIGNEE, "Carol Assignee"));

        var page = readModel.list(
                ActionItemListQuery.builder().organizationId("default").communityId(COMMUNITY).build(),
                PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(3);
        var byId = page.getContent().stream().collect(Collectors.toMap(a -> a.id(), a -> a));
        assertThat(byId.get(pinnedItem.getId()).checkpoint().sessionId()).isEqualTo(pinned);
        assertThat(byId.get(pinnedItem.getId()).checkpoint().pinned()).isTrue();
        assertThat(byId.get(pinnedItem.getId()).carryCount()).isZero();
        assertThat(byId.get(unpinnedItem.getId()).checkpoint().sessionId()).isEqualTo(soonest);
        assertThat(byId.get(unpinnedItem.getId()).checkpoint().pinned()).isFalse();
        assertThat(byId.get(unpinnedItem.getId()).carryCount()).isEqualTo(1);
        assertThat(byId.get(assignedItem.getId()).assigneeDisplayName()).isEqualTo("Carol Assignee");
    }

    @Test
    void listFiltersByOutcome() {

        seedActionItem(COMMUNITY, null);
        var closed = actionItemStore
                .save(seedActionItemBuilder(COMMUNITY, null).outcome(ActionItemOutcome.DONE).build());

        var page = readModel.list(ActionItemListQuery.builder().organizationId("default").communityId(COMMUNITY)
                .outcome(ActionItemOutcome.DONE).build(), PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(a -> a.id()).containsExactly(closed.getId());
    }

    /**
     * The read model's own {@code sessionStore} is real (this is a {@code @DataJdbcTest}), so this proves "fires no
     * resolution query" against a dedicated mock instead, the same shape {@code
     * CheckpointResolverTest.resolveAllOfAnEmptyPageFiresNoQueryAtAll} uses - a fresh {@link ActionItemReadModel} built
     * around it, used only here, so every other test keeps reading through the real database.
     */
    @Test
    void listOfAnEmptyPageResolvesNoCheckpointsAndFiresNoResolutionQuery() {

        var isolatedResolver = new CheckpointResolver(mockSessionStore, mockSeriesStore,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var isolatedReadModel = new ActionItemReadModel(actionItemCheckpointStore, isolatedResolver, userProfileAccess,
                topicAuthorizer, new ActionItemMapper(), currentUser, actionItemStore);

        var page = isolatedReadModel.list(
                ActionItemListQuery.builder().organizationId("default").communityId(COMMUNITY_WITH_NO_SESSIONS).build(),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        verify(mockSessionStore, never()).findByOrganizationIdAndCommunityIdAndIdIn(any(), any(), any());
        verify(mockSessionStore, never()).findSoonestUpcoming(any(), any(), any());
        verify(mockSeriesStore, never()).findByOrganizationIdAndIdIn(any(), any());
    }

    @Test
    void aDroppedItemMayBeReopened() {

        var item = actionItemStore
                .save(seedActionItemBuilder(COMMUNITY, null).outcome(ActionItemOutcome.DROPPED).build());

        assertThat(readModel.single(item).canReopen()).isTrue();
    }

    @Test
    void onlyAnOpenItemMayBeEdited() {

        var open = actionItemStore.save(seedActionItemBuilder(COMMUNITY, null).build());
        var dropped = actionItemStore
                .save(seedActionItemBuilder(COMMUNITY, null).outcome(ActionItemOutcome.DROPPED).build());

        assertThat(readModel.single(open).canEdit()).isTrue();
        assertThat(readModel.single(dropped).canEdit()).isFalse();
    }

    /**
     * The FK on {@code checkpoint_session_id} is {@code ON DELETE SET NULL}, so no real write path can leave a saved
     * row pointing at a session that is genuinely gone - this pins the resolver's defensive branch instead, against an
     * entity that was never persisted, the same way an organization-mismatched or otherwise dangling reference would
     * surface.
     */
    @Test
    void singleOfAnItemPinnedToASessionThatCannotBeFoundIsAnIllegalState() {

        var dangling = ActionItemRecord.builder().id(1L).organizationId("default").communityId(COMMUNITY)
                .title("Follow up").description("").raisedByUserId("alice").checkpointSessionId(999_999L)
                .outcome(ActionItemOutcome.OPEN).build();

        assertThatThrownBy(() -> readModel.single(dangling)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * A checkpoint date is rendered as a calendar day, so the zone it is read in decides which day it names. These four
     * pin where that zone comes from against a real database: a series' own column for an occurrence, the session's own
     * for a one-off, and nothing at all when neither names one.
     */
    @Test
    void anOccurrenceCheckpointCarriesItsSeriesZone() {

        // given
        var occurrence = seedOccurrence(COMMUNITY, Instant.parse("2026-10-01T22:00:00Z"), "Europe/Paris");
        var item = seedActionItem(COMMUNITY, occurrence);

        // when
        var actual = readModel.single(item);

        // then
        assertThat(actual.checkpoint().governingTimeZone()).isEqualTo("Europe/Paris");
    }

    @Test
    void aOneOffCheckpointCarriesNoZoneEvenWhenOneIsStored() {

        // given
        var oneOff = seedOneOff(COMMUNITY, Instant.parse("2026-10-01T22:00:00Z"), "Asia/Tokyo");
        var item = seedActionItem(COMMUNITY, oneOff);

        // when
        var actual = readModel.single(item);

        // then
        assertThat(actual.checkpoint().governingTimeZone()).isNull();
    }

    @Test
    void aCheckpointSessionWithNeitherZoneCarriesNull() {

        // given
        var zoneless = seedOneOff(COMMUNITY, Instant.parse("2026-10-01T22:00:00Z"), null);
        var item = seedActionItem(COMMUNITY, zoneless);

        // when
        var actual = readModel.single(item);

        // then
        assertThat(actual.checkpoint().governingTimeZone()).isNull();
    }

    /**
     * A late-bound item resolves to the community's soonest still-upcoming session, which is not its first: the zone
     * has to follow the session actually resolved to, or an item in a community that has moved between series reads its
     * day on a clock nobody kept.
     */
    @Test
    void aLateBoundCheckpointCarriesTheZoneOfTheSessionItResolvedTo() {

        // given
        seedOccurrence(COMMUNITY, Instant.parse("2026-01-05T14:00:00Z"), "America/New_York");
        seedOccurrence(COMMUNITY, Instant.parse("2026-10-01T22:00:00Z"), "Europe/Paris");
        var item = seedActionItem(COMMUNITY, null);

        // when
        var actual = readModel.single(item);

        // then
        assertThat(actual.checkpoint().governingTimeZone()).isEqualTo("Europe/Paris");
    }

    /**
     * A page whose rows resolve to sessions of three different series carries a zone per row, not one for the page -
     * and the late-bound row is on the page too, because the soonest-upcoming session it binds to is a different
     * session, in a different zone, from either pin.
     */
    @Test
    void listCarriesTheZoneOfEachRowsOwnCheckpointSession() {

        // given
        var paris = seedOccurrence(COMMUNITY, Instant.parse("2026-10-08T16:00:00Z"), "Europe/Paris");
        var tokyo = seedOccurrence(COMMUNITY, Instant.parse("2026-09-24T09:00:00Z"), "Asia/Tokyo");
        seedOccurrence(COMMUNITY, Instant.parse("2026-09-22T14:00:00Z"), "America/New_York");
        var parisItem = seedActionItem(COMMUNITY, paris);
        var tokyoItem = seedActionItem(COMMUNITY, tokyo);
        var lateBoundItem = seedActionItem(COMMUNITY, null);

        // when
        var page = readModel.list(
                ActionItemListQuery.builder().organizationId("default").communityId(COMMUNITY).build(),
                PageRequest.of(0, 20));

        // then
        var byId = page.getContent().stream().collect(Collectors.toMap(a -> a.id(), a -> a));
        assertThat(byId.get(parisItem.getId()).checkpoint().governingTimeZone()).isEqualTo("Europe/Paris");
        assertThat(byId.get(tokyoItem.getId()).checkpoint().governingTimeZone()).isEqualTo("Asia/Tokyo");
        assertThat(byId.get(lateBoundItem.getId()).checkpoint().governingTimeZone()).isEqualTo("America/New_York");
    }

    private ActionItemRecord seedActionItem(long communityId, Long checkpointSessionId) {

        return actionItemStore.save(seedActionItemBuilder(communityId, checkpointSessionId).build());
    }

    private ActionItemRecord.ActionItemRecordBuilder seedActionItemBuilder(long communityId, Long checkpointSessionId) {

        return ActionItemRecord.builder().organizationId("default").communityId(communityId).title("Follow up")
                .description("").raisedByUserId("alice").checkpointSessionId(checkpointSessionId)
                .outcome(ActionItemOutcome.OPEN);
    }

    private Long seedUpcomingSession(long communityId, Instant scheduledAt) {

        return seedOccurrence(communityId, scheduledAt, "UTC");
    }

    private Long seedOccurrence(long communityId, Instant scheduledAt, String seriesZone) {

        var seriesId = seriesStore.save(SeriesRecordMother.any().communityId(communityId).timeZone(seriesZone).build())
                .getId();
        return sessionStore.save(SessionRecordMother.any().seriesId(seriesId).communityId(communityId)
                .scheduledAt(scheduledAt).occurrenceDate(scheduledAt.atOffset(ZoneOffset.UTC).toLocalDate()).build())
                .getId();
    }

    /** A session belonging to no series, carrying whatever zone its hour was typed in - or none at all. */
    private Long seedOneOff(long communityId, Instant scheduledAt, String zone) {

        return sessionStore.save(SessionRecordMother.any().seriesId(null).communityId(communityId).timeZone(zone)
                .scheduledAt(scheduledAt).occurrenceDate(scheduledAt.atOffset(ZoneOffset.UTC).toLocalDate()).build())
                .getId();
    }

    private void seedCheckpointRow(Long actionItemId, Long sessionId, CheckpointOutcome outcome) {

        actionItemCheckpointStore.save(ActionItemCheckpointRecord.builder().organizationId("default")
                .actionItemId(actionItemId).sessionId(sessionId).outcome(outcome).decidedByUserId("alice")
                .decidedAt(Instant.now()).build());
    }
}
