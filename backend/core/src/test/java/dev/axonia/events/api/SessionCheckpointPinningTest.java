package dev.axonia.events.api;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.communication.api.MessageOutcome;
import dev.axonia.communication.api.MessageOutcomePort;
import dev.axonia.community.api.CommunicationSettings;
import dev.axonia.community.api.TopicManagementPolicy;
import dev.axonia.events.adapter.out.persistence.ActionItemCheckpointRepository;
import dev.axonia.events.adapter.out.persistence.ActionItemRepository;
import dev.axonia.events.adapter.out.persistence.JdbcActionItemCheckpointStore;
import dev.axonia.events.adapter.out.persistence.JdbcActionItemStore;
import dev.axonia.events.adapter.out.persistence.JdbcSeriesStore;
import dev.axonia.events.adapter.out.persistence.JdbcSessionStore;
import dev.axonia.events.adapter.out.persistence.JdbcTopicStore;
import dev.axonia.events.adapter.out.persistence.SeriesRepository;
import dev.axonia.events.adapter.out.persistence.SessionRepository;
import dev.axonia.events.adapter.out.persistence.TopicRepository;
import dev.axonia.events.application.ActionItemCloser;
import dev.axonia.events.application.ActionItemMapper;
import dev.axonia.events.application.ActionItemReadModel;
import dev.axonia.events.application.ActionItemService;
import dev.axonia.events.application.CheckpointDecisionPass;
import dev.axonia.events.application.CheckpointResolver;
import dev.axonia.events.application.CloseSessionRequest;
import dev.axonia.events.application.FacilitatorResolver;
import dev.axonia.events.application.OccurrenceSkipService;
import dev.axonia.events.application.SectionTypeCatalog;
import dev.axonia.events.application.SessionAuthorizer;
import dev.axonia.events.application.SessionBatchLookups;
import dev.axonia.events.application.SessionCommunityGuard;
import dev.axonia.events.application.SessionCreation;
import dev.axonia.events.application.SessionInstant;
import dev.axonia.events.application.SessionMapper;
import dev.axonia.events.application.SessionService;
import dev.axonia.events.application.TopicAuthorizer;
import dev.axonia.events.application.TopicMemberResolver;
import dev.axonia.events.application.UpcomingReleasePolicy;
import dev.axonia.events.application.port.out.ActionItemCheckpointStore;
import dev.axonia.events.application.port.out.ActionItemStore;
import dev.axonia.events.application.port.out.AttendanceStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SectionTypeStore;
import dev.axonia.events.application.port.out.SeriesStore;
import dev.axonia.events.application.port.out.SessionListing;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.application.port.out.UserProfileAccess;
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.SeriesRecordMother;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.shared.api.metrics.UsageCounters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The one moment a late binding resolves: holding a session pins every open, unpinned item of that community to it.
 * Cancelling a session or skipping an occurrence in advance is the mirror - a session that will never answer anything
 * clears the pins that named it, so those items late-bind again to whatever comes next.
 */
@ExtendWith(MockitoExtension.class)
class SessionCheckpointPinningTest extends CoreJdbcTestSupport {

    private static final long COMMUNITY = 1L;
    private static final long OTHER_COMMUNITY = 2L;

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private SessionStore sessionStore;

    private SeriesStore seriesStore;

    private TopicStore topicStore;

    private ActionItemStore actionItemStore;

    private ActionItemCheckpointStore actionItemCheckpointStore;

    @Autowired
    void wireStores(SessionRepository sessionRepository, SeriesRepository seriesRepository,
            TopicRepository topicRepository, ActionItemRepository actionItemRepository,
            ActionItemCheckpointRepository actionItemCheckpointRepository) {

        sessionStore = new JdbcSessionStore(sessionRepository);
        seriesStore = new JdbcSeriesStore(seriesRepository);
        topicStore = new JdbcTopicStore(topicRepository);
        actionItemStore = new JdbcActionItemStore(actionItemRepository);
        actionItemCheckpointStore = new JdbcActionItemCheckpointStore(actionItemCheckpointRepository);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Mock
    private SectionTypeStore sectionTypeStore;

    @Mock
    private AttendanceStore attendanceStore;

    @Mock
    private SessionListing sessionListing;

    @Mock
    private SessionAuthorizer sessionAuthorizer;

    @Mock
    private CheckpointDecisionPass checkpointDecisionPass;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private MessageOutcomePort messageOutcomePort;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserProfileAccess userProfileAccess;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private SessionService sessionService;
    private OccurrenceSkipService occurrenceSkipService;
    private ActionItemService actionItemService;

    private Long seriesId;
    private Long sessionId;
    private Long occurrenceSessionId;
    private LocalDate occurrenceDate;

    @BeforeEach
    void seedCommunities() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING");
        for (var id : List.of(COMMUNITY, OTHER_COMMUNITY)) {
            jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                    + "created_at, updated_at, version) SELECT ?,'default','c' || ?,'C', k.id,'','ACTIVE', now(), "
                    + "now(), 0 FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' "
                    + "ON CONFLICT DO NOTHING", id, id);
        }
    }

    @BeforeEach
    void wireServices() {

        var mapper = new SessionMapper(communityAccess, messageOutcomePort,
                new SessionBatchLookups(sectionTypeStore, attendanceStore, seriesStore));
        var upcomingReleasePolicy = new UpcomingReleasePolicy(communityAccess, clock);
        var sectionTypeCatalog = new SectionTypeCatalog(sectionTypeStore);
        var facilitatorResolver = new FacilitatorResolver(communityAccess);
        var sessionCreation = new SessionCreation(upcomingReleasePolicy);
        sessionService = new SessionService(sessionStore, seriesStore, topicStore, actionItemStore,
                checkpointDecisionPass, mapper, sessionListing, sessionCreation, sessionAuthorizer, communityAccess,
                currentUser, organizationContext, eventPublisher, clock, upcomingReleasePolicy,
                new SessionInstant(seriesStore), new UsageCounters(new SimpleMeterRegistry()), sectionTypeCatalog,
                facilitatorResolver);
        occurrenceSkipService = new OccurrenceSkipService(sessionStore, seriesStore, mapper, sessionCreation,
                communityAccess, currentUser, organizationContext, eventPublisher, clock, actionItemStore);

        var checkpointResolver = new CheckpointResolver(sessionStore, seriesStore, clock);
        var topicAuthorizer = new TopicAuthorizer(communityAccess);
        var actionItemMapper = new ActionItemMapper();
        var actionItemReadModel = new ActionItemReadModel(actionItemCheckpointStore, checkpointResolver,
                userProfileAccess, topicAuthorizer, actionItemMapper, currentUser, actionItemStore);
        var topicMemberResolver = new TopicMemberResolver(communityAccess);
        var sessionCommunityGuard = new SessionCommunityGuard(sessionStore, clock);
        var actionItemCloser = new ActionItemCloser(actionItemStore, actionItemMapper, eventPublisher);
        actionItemService = new ActionItemService(actionItemStore, actionItemReadModel, actionItemCloser,
                communityAccess, currentUser, organizationContext, eventPublisher, topicMemberResolver,
                sessionCommunityGuard, topicAuthorizer, sessionStore, clock);
    }

    @BeforeEach
    void seedSeams() {

        when(organizationContext.organizationId()).thenReturn("default");
        lenient().when(currentUser.userId()).thenReturn("alice");
        lenient().when(communityAccess.isMember(anyLong(), any())).thenReturn(true);
        lenient().when(communityAccess.isOrganizer(anyLong(), any())).thenReturn(true);
        lenient().when(communityAccess.topicManagementPolicy(anyLong())).thenReturn(TopicManagementPolicy.ANY_MEMBER);
        lenient().when(communityAccess.communicationSettings(anyLong()))
                .thenReturn(new CommunicationSettings(true, true, 3, 24));
        lenient().when(communityAccess.communicationSettings(any(), anyLong()))
                .thenReturn(new CommunicationSettings(true, true, 3, 24));
        lenient().when(communityAccess.memberUserIds(any(), anyLong())).thenReturn(List.of());
        lenient().when(messageOutcomePort.outcomeFor(any(), any())).thenReturn(MessageOutcome.builder().build());
        lenient().when(attendanceStore.countBySessionIdIn(any(), any())).thenReturn(List.of());
    }

    /**
     * A session already due (for {@code close} and {@code cancel}) and a distinct, still-future occurrence of the same
     * series (for {@code skip}/{@code unskip}), both offsets from a captured {@code now} rather than literal calendar
     * dates - what actually matters here is the ordering against the clock, and pinning that with a literal is how a
     * hardcoded date that expires comes back.
     *
     * <p>
     * {@code sessionId} sits a full day behind {@code NOW}, not just an hour: {@code pinLateBound}'s
     * {@code created_at < scheduledAt} predicate needs room between the session's own scheduled instant and the real
     * wall-clock instant a freshly-raised item is stamped with, which {@code close}'s own "already due" requirement
     * pins arbitrarily close to {@code NOW} otherwise.
     */
    @BeforeEach
    void seedSessionUnderTest() {

        seriesId = seriesStore
                .save(SeriesRecordMother.any().organizationId("default").communityId(COMMUNITY).timeZone("UTC").build())
                .getId();

        var pastAt = NOW.minus(1, ChronoUnit.DAYS);
        sessionId = sessionStore.save(SessionRecordMother.any().organizationId("default").seriesId(seriesId)
                .communityId(COMMUNITY).scheduledAt(pastAt).occurrenceDate(pastAt.atZone(ZoneOffset.UTC).toLocalDate())
                .sections(List.of()).build()).getId();

        var futureAt = NOW.plus(60, ChronoUnit.DAYS);
        occurrenceDate = futureAt.atZone(ZoneOffset.UTC).toLocalDate();
        occurrenceSessionId = sessionStore
                .save(SessionRecordMother.any().organizationId("default").seriesId(seriesId).communityId(COMMUNITY)
                        .scheduledAt(futureAt).occurrenceDate(occurrenceDate).sections(List.of()).build())
                .getId();
    }

    @Test
    void holdingASessionPinsEveryLateBoundItemOfThatCommunity() {

        var alreadyWaiting = NOW.minus(10, ChronoUnit.DAYS);
        var first = raiseLateBound(COMMUNITY, "Close the Jira ticket", alreadyWaiting);
        var second = raiseLateBound(COMMUNITY, "Chase the vendor", alreadyWaiting);
        var versionBefore = reload(first).getVersion();

        sessionService.close(sessionId, new CloseSessionRequest(true));

        var reloaded = reload(first);
        assertThat(reloaded.getCheckpointSessionId()).isEqualTo(sessionId);
        assertThat(reload(second).getCheckpointSessionId()).isEqualTo(sessionId);
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore + 1);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(NOW);
    }

    /**
     * {@code pinLateBound}'s {@code created_at < scheduledAt} predicate: an item raised after this session already
     * happened, but before an organizer got round to closing it, was never waiting on it. The read side resolves a null
     * checkpoint to the community's next still-upcoming session, so retro-pinning it here to a session already in the
     * past would silently disagree with what the item already reads as.
     */
    @Test
    void holdingASessionLeavesAnItemRaisedAfterItUnpinned() {

        var item = raiseLateBound(COMMUNITY, "Just noticed this");

        sessionService.close(sessionId, new CloseSessionRequest(true));

        assertThat(reload(item).getCheckpointSessionId()).isNull();
    }

    @Test
    void holdingASessionLeavesAnItemPinnedElsewhereAlone() {

        var later = seedSession(COMMUNITY, NOW.plus(90, ChronoUnit.DAYS));
        // backdated so that, were the checkpoint_session_id IS NULL predicate ever dropped, this would pin - isolating
        // exactly the predicate under test rather than passing for the unrelated reason the item was never waiting
        var item = raisePinned(COMMUNITY, "Renew the certificate", later, NOW.minus(10, ChronoUnit.DAYS));

        sessionService.close(sessionId, new CloseSessionRequest(true));

        assertThat(reload(item).getCheckpointSessionId()).isEqualTo(later);
    }

    /**
     * The silent fourth outcome the checkpoint exists to remove. An organizer closes a session as held, items pin to
     * it, and the "After the session" dialog is never opened - nothing requires it. Without the recovery arm those
     * items are pinned to a session that will never answer them, so every later hold skips them for being pinned and no
     * follow-up pass ever lists them again.
     */
    @Test
    void holdingASessionRePinsAnItemStrandedOnAnEarlierUnansweredOne() {

        var earlier = seedSession(COMMUNITY, NOW.minus(3, ChronoUnit.DAYS));
        var item = raiseLateBound(COMMUNITY, "Chase the vendor", NOW.minus(10, ChronoUnit.DAYS));
        sessionService.close(earlier, new CloseSessionRequest(true));
        var versionBefore = reload(item).getVersion();

        sessionService.close(sessionId, new CloseSessionRequest(true));

        var reloaded = reload(item);
        assertThat(reloaded.getCheckpointSessionId()).isEqualTo(sessionId);
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore + 1);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(NOW);
    }

    /**
     * The {@code created_at < scheduledAt} discriminator gates the recovery arm too, so the two arms cannot disagree
     * about what "was already waiting" means: an item raised after this session's scheduled instant was never waiting
     * on it, however dead the session its pin names.
     */
    @Test
    void holdingASessionLeavesAStrandedItemRaisedAfterItWhereItIs() {

        var earlier = seedSettledSession(COMMUNITY, NOW.minus(2, ChronoUnit.HOURS));
        var item = raisePinned(COMMUNITY, "Just noticed this", earlier, NOW.minus(12, ChronoUnit.HOURS));

        sessionService.close(sessionId, new CloseSessionRequest(true));

        assertThat(reload(item).getCheckpointSessionId()).isEqualTo(earlier);
    }

    /**
     * An item an organizer pinned to this very session while it was still upcoming is already where it belongs. The
     * recovery arm sees this session settled the moment {@code close} stamps {@code closed_at}, so it is excluded by
     * id: a pin rewritten to itself would change nothing but still churn {@code updated_at}, a sort key members read.
     */
    @Test
    void holdingASessionDoesNotRewriteAPinThatAlreadyNamesIt() {

        var item = raisePinned(COMMUNITY, "Renew the certificate", sessionId, NOW.minus(10, ChronoUnit.DAYS));
        var versionBefore = reload(item).getVersion();

        sessionService.close(sessionId, new CloseSessionRequest(true));

        var reloaded = reload(item);
        assertThat(reloaded.getCheckpointSessionId()).isEqualTo(sessionId);
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore);
    }

    @Test
    void holdingASessionLeavesAnotherCommunitysItemsAlone() {

        // backdated so that, were the community predicate ever dropped, this would pin - isolating exactly the
        // predicate under test rather than passing for the unrelated reason the item was never waiting either
        var item = raiseLateBound(OTHER_COMMUNITY, "Not our problem", NOW.minus(10, ChronoUnit.DAYS));

        sessionService.close(sessionId, new CloseSessionRequest(true));

        assertThat(reload(item).getCheckpointSessionId()).isNull();
    }

    /**
     * Closing takes the pin itself, so the item is already this session's record before the session is held, and
     * {@code pinLateBound}'s {@code outcome != OPEN} predicate then has nothing left to do. Asserted on both sides of
     * the hold: the first assertion is the pin close took, the second that holding left it alone.
     */
    @Test
    void aClosedItemIsAlreadyPinnedAndHoldingLeavesItAlone() {

        // backdated for the same reason: isolate the outcome != OPEN predicate from the created_at one
        var item = raiseLateBound(COMMUNITY, "Already done", NOW.minus(10, ChronoUnit.DAYS));
        actionItemService.close(item);
        var pinnedAtClose = reload(item).getCheckpointSessionId();
        // Not this session: closing pins to whichever session is still upcoming, and this one is the one being held.
        assertThat(pinnedAtClose).isNotNull().isNotEqualTo(sessionId);

        sessionService.close(sessionId, new CloseSessionRequest(true));

        assertThat(reload(item).getCheckpointSessionId()).isEqualTo(pinnedAtClose);
    }

    /**
     * {@code pinLateBound}'s {@code organization_id = :organizationId} predicate. The item's {@code community_id}
     * deliberately matches this session's own community, so only {@code organization_id} excludes it - the
     * Testcontainers connection bypasses Row-Level Security by table ownership, so this predicate, not RLS, is what
     * stands between closing a session and writing a checkpoint onto another organization's item in this test
     * environment.
     */
    @Test
    void holdingASessionLeavesAnotherOrganizationsItemUnpinned() {

        var item = raiseLateBoundInOrganization("other", COMMUNITY, "Not our tenant", NOW.minus(10, ChronoUnit.DAYS));

        sessionService.close(sessionId, new CloseSessionRequest(true));

        assertThat(reload("other", item).getCheckpointSessionId()).isNull();
    }

    @Test
    void callingASessionOffClearsThePinsThatNamedIt() {

        var item = raisePinned(COMMUNITY, "Renew the certificate", sessionId);
        var versionBefore = reload(item).getVersion();

        sessionService.cancel(sessionId);

        var reloaded = reload(item);
        assertThat(reloaded.getCheckpointSessionId()).isNull();
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore + 1);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(NOW);
    }

    /**
     * {@code clearPinsFor}'s {@code checkpoint_session_id = :sessionId} predicate. Without it, cancelling one session
     * would clear every open item's pin in the organization, not just the ones that named this session.
     */
    @Test
    void callingASessionOffLeavesAnItemPinnedElsewhereAlone() {

        var item = raisePinned(COMMUNITY, "Not this one", occurrenceSessionId);

        sessionService.cancel(sessionId);

        assertThat(reload(item).getCheckpointSessionId()).isEqualTo(occurrenceSessionId);
    }

    /**
     * {@code clearPinsFor}'s own {@code organization_id} predicate, which nothing covered: session ids come from one
     * global sequence, so another tenant's item cannot name this session by accident - but the read is not allowed to
     * rest on that.
     */
    @Test
    void callingASessionOffLeavesAnotherOrganizationsPinAlone() {

        var item = actionItemStore.save(itemBuilder(COMMUNITY, "Another tenant's").organizationId("other-org")
                .checkpointSessionId(sessionId).build()).getId();

        sessionService.cancel(sessionId);

        assertThat(reloadIn("other-org", item).getCheckpointSessionId()).isEqualTo(sessionId);
    }

    /** The community half of the same predicate: a pin from another community of this tenant stays put. */
    @Test
    void callingASessionOffLeavesAnotherCommunitysPinAlone() {

        var item = raisePinned(OTHER_COMMUNITY, "Not our problem", sessionId);

        sessionService.cancel(sessionId);

        assertThat(reload(item).getCheckpointSessionId()).isEqualTo(sessionId);
    }

    @Test
    void theNoAnswerToCloseClearsPinsTheSameWayCancelDoes() {

        var item = raisePinned(COMMUNITY, "Renew the certificate", sessionId);

        sessionService.close(sessionId, new CloseSessionRequest(false));

        assertThat(reload(item).getCheckpointSessionId()).isNull();
    }

    @Test
    void skippingAnOccurrenceClearsThePinsThatNamedIt() {

        var item = raisePinned(COMMUNITY, "Renew the certificate", occurrenceSessionId);

        occurrenceSkipService.skip(seriesId, occurrenceDate);

        assertThat(reload(item).getCheckpointSessionId()).isNull();
    }

    /**
     * Clearing is not reversible on purpose: the item late-binds to whatever comes next, which may no longer be this
     * occurrence. Re-pinning it would silently override a checkpoint moved in between.
     */
    @Test
    void unskippingDoesNotResurrectThePin() {

        var item = raisePinned(COMMUNITY, "Renew the certificate", occurrenceSessionId);
        occurrenceSkipService.skip(seriesId, occurrenceDate);

        occurrenceSkipService.unskip(seriesId, occurrenceDate);

        assertThat(reload(item).getCheckpointSessionId()).isNull();
    }

    private Long raiseLateBound(long communityId, String title) {

        return actionItemStore.save(itemBuilder(communityId, title).build()).getId();
    }

    /**
     * Raises an item and backdates its {@code created_at} straight in the database, bypassing {@code @CreatedDate}
     * auditing (which stamps the real wall clock, not this suite's fixed one) - the only way to put an item's creation
     * genuinely before a session that is itself seeded in the past.
     */
    private Long raiseLateBound(long communityId, String title, Instant createdAt) {

        var id = actionItemStore.save(itemBuilder(communityId, title).build()).getId();
        jdbc.update("UPDATE action_item SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), id);
        return id;
    }

    private Long raisePinned(long communityId, String title, Long checkpointSessionId) {

        return actionItemStore.save(itemBuilder(communityId, title).checkpointSessionId(checkpointSessionId).build())
                .getId();
    }

    /** {@link #raiseLateBound(long, String, Instant)}, but already pinned - same backdating, same reason. */
    private Long raisePinned(long communityId, String title, Long checkpointSessionId, Instant createdAt) {

        var id = actionItemStore.save(itemBuilder(communityId, title).checkpointSessionId(checkpointSessionId).build())
                .getId();
        jdbc.update("UPDATE action_item SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), id);
        return id;
    }

    /** {@link #raiseLateBound(long, String, Instant)}, but in a different organization than the session under test. */
    private Long raiseLateBoundInOrganization(String organizationId, long communityId, String title,
            Instant createdAt) {

        var id = actionItemStore.save(itemBuilder(communityId, title).organizationId(organizationId).build()).getId();
        jdbc.update("UPDATE action_item SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), id);
        return id;
    }

    private ActionItemRecord.ActionItemRecordBuilder itemBuilder(long communityId, String title) {

        return ActionItemRecord.builder().organizationId("default").communityId(communityId).title(title)
                .description("").raisedByUserId("alice").outcome(ActionItemOutcome.OPEN);
    }

    private ActionItemRecord reload(Long id) {

        return reloadIn("default", id);
    }

    private ActionItemRecord reloadIn(String organizationId, Long id) {

        return actionItemStore.findByOrganizationIdAndId(organizationId, id).orElseThrow();
    }

    private ActionItemRecord reload(String organizationId, Long id) {

        return actionItemStore.findByOrganizationIdAndId(organizationId, id).orElseThrow();
    }

    /** A scheduled session of its own series, so seeding one never disturbs the series under test. */
    private Long seedSession(long communityId, Instant scheduledAt) {

        return seedSession(communityId, scheduledAt, builder -> builder);
    }

    /** The same, already closed out as held - a session that has come and gone without answering anything. */
    private Long seedSettledSession(long communityId, Instant scheduledAt) {

        return seedSession(communityId, scheduledAt, b -> b.status("HELD").closedAt(scheduledAt));
    }

    private Long seedSession(long communityId, Instant scheduledAt,
            UnaryOperator<SessionRecord.SessionRecordBuilder> lifecycle) {

        var ownSeriesId = seriesStore
                .save(SeriesRecordMother.any().organizationId("default").communityId(communityId).build()).getId();
        return sessionStore
                .save(lifecycle
                        .apply(SessionRecordMother.any().organizationId("default").seriesId(ownSeriesId)
                                .communityId(communityId).scheduledAt(scheduledAt)
                                .occurrenceDate(scheduledAt.atZone(ZoneOffset.UTC).toLocalDate()).sections(List.of()))
                        .build())
                .getId();
    }
}
