package dev.axonia.events.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import dev.axonia.events.adapter.out.persistence.ActionItemCheckpointEntity;
import dev.axonia.events.adapter.out.persistence.ActionItemCheckpointRepository;
import dev.axonia.events.adapter.out.persistence.ActionItemRepository;
import dev.axonia.events.adapter.out.persistence.JdbcActionItemCheckpointStore;
import dev.axonia.events.adapter.out.persistence.JdbcActionItemStore;
import dev.axonia.events.adapter.out.persistence.JdbcSectionTypeStore;
import dev.axonia.events.adapter.out.persistence.JdbcSeriesStore;
import dev.axonia.events.adapter.out.persistence.JdbcSessionStore;
import dev.axonia.events.adapter.out.persistence.JdbcTopicStore;
import dev.axonia.events.adapter.out.persistence.SectionTypeRepository;
import dev.axonia.events.adapter.out.persistence.SeriesRepository;
import dev.axonia.events.adapter.out.persistence.SessionRepository;
import dev.axonia.events.adapter.out.persistence.TopicRepository;
import dev.axonia.events.application.ActionItemCloser;
import dev.axonia.events.application.ActionItemLifecycle;
import dev.axonia.events.application.ActionItemMapper;
import dev.axonia.events.application.CheckpointDecisionPass;
import dev.axonia.events.application.FacilitatorResolver;
import dev.axonia.events.application.FollowUpRequest;
import dev.axonia.events.application.SectionTypeCatalog;
import dev.axonia.events.application.SessionAuthorizer;
import dev.axonia.events.application.SessionBatchLookups;
import dev.axonia.events.application.SessionCreation;
import dev.axonia.events.application.SessionInstant;
import dev.axonia.events.application.SessionMapper;
import dev.axonia.events.application.SessionService;
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
import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.ActionItemTag;
import dev.axonia.events.domain.CheckpointDecision;
import dev.axonia.events.domain.CheckpointOutcome;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SectionTypeRecordMother;
import dev.axonia.events.domain.SeriesRecordMother;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicRecordMother;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.metrics.UsageCounters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * A session's follow-up pass is where the room answers the work it owes: every open item still pinned to this session
 * gets an outcome. DONE closes it and credits the assignee; CARRIED sends it back to late binding for whatever session
 * comes next; DROPPED closes it without credit. Every non-action slot goes DONE the same way it always did - there is
 * no more action-shaped exception in that loop, because an action item never occupied a slot to begin with.
 */
@ExtendWith(MockitoExtension.class)
class SessionFollowUpOutcomeTest extends CoreJdbcTestSupport {

    private static final long COMMUNITY = 1L;
    private static final long OTHER_COMMUNITY = 2L;
    private static final String BOB = "bob";

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private SessionStore sessionStore;

    private SeriesStore seriesStore;

    private TopicStore topicStore;

    private ActionItemStore actionItemStore;

    private ActionItemCheckpointStore actionItemCheckpointStore;

    private ActionItemCheckpointRepository actionItemCheckpointRepository;

    private SectionTypeStore sectionTypeStore;

    @Autowired
    void wireStores(SessionRepository sessionRepository, SeriesRepository seriesRepository,
            TopicRepository topicRepository, ActionItemRepository actionItemRepository,
            ActionItemCheckpointRepository actionItemCheckpointRepository,
            SectionTypeRepository sectionTypeRepository) {

        sessionStore = new JdbcSessionStore(sessionRepository);
        seriesStore = new JdbcSeriesStore(seriesRepository);
        topicStore = new JdbcTopicStore(topicRepository);
        actionItemStore = new JdbcActionItemStore(actionItemRepository);
        this.actionItemCheckpointRepository = actionItemCheckpointRepository;
        actionItemCheckpointStore = new JdbcActionItemCheckpointStore(actionItemCheckpointRepository);
        sectionTypeStore = new JdbcSectionTypeStore(sectionTypeRepository);
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Mock
    private AttendanceStore attendanceStore;

    @Mock
    private SessionListing sessionListing;

    @Mock
    private SessionAuthorizer sessionAuthorizer;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private MessageOutcomePort messageOutcomePort;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final RecordingEvents events = new RecordingEvents();

    private SessionService sessionService;

    private Long seriesId;
    private Long sessionId;
    private Long otherSessionId;

    @BeforeEach
    void wireServices() {

        var mapper = new SessionMapper(communityAccess, messageOutcomePort,
                new SessionBatchLookups(sectionTypeStore, attendanceStore, seriesStore));
        var upcomingReleasePolicy = new UpcomingReleasePolicy(communityAccess, clock);
        var sectionTypeCatalog = new SectionTypeCatalog(sectionTypeStore);
        var facilitatorResolver = new FacilitatorResolver(communityAccess);
        var sessionCreation = new SessionCreation(upcomingReleasePolicy);
        var actionItemMapper = new ActionItemMapper();
        var actionItemCloser = new ActionItemCloser(actionItemStore, actionItemMapper, events);
        var actionItemLifecycle = new ActionItemLifecycle(actionItemStore, actionItemCheckpointStore, actionItemCloser);
        var checkpointDecisionPass = new CheckpointDecisionPass(actionItemStore, actionItemLifecycle);
        sessionService = new SessionService(sessionStore, seriesStore, topicStore, actionItemStore,
                checkpointDecisionPass, mapper, sessionListing, sessionCreation, sessionAuthorizer, communityAccess,
                currentUser, organizationContext, events, clock, upcomingReleasePolicy, new SessionInstant(seriesStore),
                new UsageCounters(new SimpleMeterRegistry()), sectionTypeCatalog, facilitatorResolver);
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

    @BeforeEach
    void seedSessionsUnderTest() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING");
        for (var id : List.of(COMMUNITY, OTHER_COMMUNITY)) {
            jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                    + "created_at, updated_at, version) SELECT ?,'default','c' || ?,'C', k.id,'','ACTIVE', now(), "
                    + "now(), 0 FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' "
                    + "ON CONFLICT DO NOTHING", id, id);
        }

        seriesId = seriesStore
                .save(SeriesRecordMother.any().organizationId("default").communityId(COMMUNITY).timeZone("UTC").build())
                .getId();

        var heldAt = NOW.minus(1, ChronoUnit.DAYS);
        sessionId = sessionStore.save(SessionRecordMother.any().organizationId("default").seriesId(seriesId)
                .communityId(COMMUNITY).status("HELD").scheduledAt(heldAt)
                .occurrenceDate(heldAt.atZone(ZoneOffset.UTC).toLocalDate()).sections(List.of()).build()).getId();

        var elsewhereAt = NOW.minus(2, ChronoUnit.DAYS);
        otherSessionId = sessionStore
                .save(SessionRecordMother.any().organizationId("default").seriesId(seriesId).communityId(COMMUNITY)
                        .status("HELD").scheduledAt(elsewhereAt)
                        .occurrenceDate(elsewhereAt.atZone(ZoneOffset.UTC).toLocalDate()).sections(List.of()).build())
                .getId();
    }

    @Test
    void aDoneDecisionClosesTheItemAndLogsTheSession() {

        var item = pinnedOpenItem(sessionId, BOB, List.of("ci"));

        sessionService.recordFollowUp(sessionId, followUp(decision(item, CheckpointOutcome.DONE)));

        assertThat(reload(item).getOutcome()).isEqualTo(ActionItemOutcome.DONE);
        assertThat(checkpointRows(item)).singleElement().satisfies(row -> {
            assertThat(row.getSessionId()).isEqualTo(sessionId);
            assertThat(row.getDecidedByUserId()).isEqualTo("alice");
            assertThat(row.getDecidedAt()).isEqualTo(NOW);
        });
        // The one test reaching findOpenByCheckpointSession, so the only proof its @Query hydrates tags at all -
        // and empty tags make the expertise listener delete the signal source instead of crediting it.
        assertThat(events.stream(ActionItemClosed.class)).singleElement()
                .satisfies(closed -> assertThat(closed.tags()).containsExactly("ci"));
    }

    @Test
    void aCarriedDecisionReturnsTheItemToLateBinding() {

        var item = pinnedOpenItem(sessionId, BOB, List.of());

        sessionService.recordFollowUp(sessionId, followUp(decision(item, CheckpointOutcome.CARRIED)));

        var reloaded = reload(item);
        assertThat(reloaded.getOutcome()).isEqualTo(ActionItemOutcome.OPEN);
        assertThat(reloaded.getCheckpointSessionId()).isNull();
        assertThat(checkpointRows(item)).singleElement()
                .satisfies(row -> assertThat(row.getOutcome()).isEqualTo(CheckpointOutcome.CARRIED));
        assertThat(events.stream(ActionItemClosed.class)).isEmpty();
    }

    @Test
    void aDroppedDecisionClosesWithoutCredit() {

        var item = pinnedOpenItem(sessionId, BOB, List.of("infra"));

        sessionService.recordFollowUp(sessionId, followUp(decision(item, CheckpointOutcome.DROPPED)));

        assertThat(reload(item).getOutcome()).isEqualTo(ActionItemOutcome.DROPPED);
        assertThat(events.stream(ActionItemClosed.class)).isEmpty();
    }

    @Test
    void aSecondPassReplayingTheSameDecisionIsRefused() {

        var item = pinnedOpenItem(sessionId, BOB, List.of());
        sessionService.recordFollowUp(sessionId, followUp(decision(item, CheckpointOutcome.DONE)));

        assertThatThrownBy(
                () -> sessionService.recordFollowUp(sessionId, followUp(decision(item, CheckpointOutcome.DONE))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("NOT_PINNED_HERE");

        assertThat(checkpointRows(item)).hasSize(1);
        assertThat(events.stream(ActionItemClosed.class)).hasSize(1);
    }

    @Test
    void aDecisionNamingAnItemNotPinnedHereIsRefused() {

        var elsewhere = pinnedOpenItem(otherSessionId, BOB, List.of());

        assertThatThrownBy(
                () -> sessionService.recordFollowUp(sessionId, followUp(decision(elsewhere, CheckpointOutcome.DONE))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("NOT_PINNED_HERE");
    }

    @Test
    void aDecisionNamingAnItemOfAForeignCommunityIsRefused() {

        var foreign = pinnedOpenItem(sessionId, BOB, List.of(), OTHER_COMMUNITY);

        assertThatThrownBy(
                () -> sessionService.recordFollowUp(sessionId, followUp(decision(foreign, CheckpointOutcome.DONE))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("NOT_PINNED_HERE");
    }

    @Test
    void duplicateDecisionsForTheSameItemAreRefused() {

        var item = pinnedOpenItem(sessionId, BOB, List.of());

        assertThatThrownBy(() -> sessionService.recordFollowUp(sessionId,
                followUp(decision(item, CheckpointOutcome.DONE), decision(item, CheckpointOutcome.CARRIED))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("DUPLICATE_DECISION");

        assertThat(reload(item).getOutcome()).isEqualTo(ActionItemOutcome.OPEN);
        assertThat(checkpointRows(item)).isEmpty();
    }

    @Test
    void aRequestTopicStillGoesDoneWhenTheSessionIsHeld() {

        var topic = slottedTopic(sessionId, TopicKind.REQUEST);

        sessionService.recordFollowUp(sessionId, followUp());

        assertThat(reloadTopic(topic).getStatus()).isEqualTo(TopicStatus.DONE);
    }

    private Long pinnedOpenItem(Long checkpointSessionId, String assignee, List<String> tags) {

        return pinnedOpenItem(checkpointSessionId, assignee, tags, COMMUNITY);
    }

    private Long pinnedOpenItem(Long checkpointSessionId, String assignee, List<String> tags, long communityId) {

        var tagRefs = tags.stream().map(t -> ActionItemTag.builder().tag(t).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return actionItemStore
                .save(ActionItemRecord.builder().organizationId("default").communityId(communityId)
                        .title("Do the thing").description("").raisedByUserId("carol").assigneeUserId(assignee)
                        .checkpointSessionId(checkpointSessionId).outcome(ActionItemOutcome.OPEN).tags(tagRefs).build())
                .getId();
    }

    private Long slottedTopic(Long ofSessionId, TopicKind kind) {

        var topicId = topicStore
                .save(TopicRecordMother.any().kind(kind).communityId(COMMUNITY).status(TopicStatus.SCHEDULED).build())
                .getId();
        var sectionTypeId = sectionTypeStore.save(SectionTypeRecordMother.any().organizationId("default").build())
                .getId();
        var session = sessionStore.findByOrganizationIdAndId("default", ofSessionId).orElseThrow();
        var slot = SlotRecord.builder().title("Talk").topicId(topicId).durationMinutes(15).presenters(Set.of()).build();
        var section = SectionRecord.builder().sectionTypeId(sectionTypeId).title("Talks")
                .slots(new ArrayList<>(List.of(slot))).build();
        sessionStore.save(session.toBuilder().sections(new ArrayList<>(List.of(section))).build());
        return topicId;
    }

    private ActionItemRecord reload(Long id) {

        return actionItemStore.findByOrganizationIdAndId("default", id).orElseThrow();
    }

    private TopicRecord reloadTopic(Long id) {

        return topicStore.findByOrganizationIdAndId("default", id).orElseThrow();
    }

    private List<ActionItemCheckpointEntity> checkpointRows(Long actionItemId) {

        var all = new ArrayList<ActionItemCheckpointEntity>();
        actionItemCheckpointRepository.findAll().forEach(all::add);
        return all.stream().filter(row -> actionItemId.equals(row.getActionItemId())).toList();
    }

    private FollowUpRequest followUp(CheckpointDecision... decisions) {

        return new FollowUpRequest("notes", null, List.of(decisions));
    }

    private CheckpointDecision decision(Long actionItemId, CheckpointOutcome outcome) {

        return new CheckpointDecision(actionItemId, outcome);
    }

    private static final class RecordingEvents implements ApplicationEventPublisher {

        private final List<Object> published = new ArrayList<>();

        @Override
        public void publishEvent(Object event) {

            published.add(event);
        }

        <T> Stream<T> stream(Class<T> type) {

            return published.stream().filter(type::isInstance).map(type::cast);
        }
    }
}
