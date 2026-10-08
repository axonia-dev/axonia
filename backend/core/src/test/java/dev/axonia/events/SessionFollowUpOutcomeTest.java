package dev.axonia.events;

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

import dev.axonia.ConflictException;
import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.UsageCounters;
import dev.axonia.communication.MessageOutcome;
import dev.axonia.communication.MessageOutcomePort;
import dev.axonia.community.CommunicationSettings;
import dev.axonia.community.TopicManagementPolicy;
import dev.axonia.events.internal.ActionItemCheckpointEntity;
import dev.axonia.events.internal.ActionItemCheckpointRepository;
import dev.axonia.events.internal.ActionItemCloser;
import dev.axonia.events.internal.ActionItemEntity;
import dev.axonia.events.internal.ActionItemLifecycle;
import dev.axonia.events.internal.ActionItemMapper;
import dev.axonia.events.internal.ActionItemRepository;
import dev.axonia.events.internal.ActionItemTagRef;
import dev.axonia.events.internal.AttendanceRepository;
import dev.axonia.events.internal.CheckpointDecisionPass;
import dev.axonia.events.internal.FacilitatorResolver;
import dev.axonia.events.internal.SectionEntity;
import dev.axonia.events.internal.SectionTypeCatalog;
import dev.axonia.events.internal.SectionTypeRepository;
import dev.axonia.events.internal.SeriesRepository;
import dev.axonia.events.internal.SessionAuthorizer;
import dev.axonia.events.internal.SessionBatchLookups;
import dev.axonia.events.internal.SessionCreation;
import dev.axonia.events.internal.SessionInstant;
import dev.axonia.events.internal.SessionListReader;
import dev.axonia.events.internal.SessionMapper;
import dev.axonia.events.internal.SessionRepository;
import dev.axonia.events.internal.SlotEntity;
import dev.axonia.events.internal.TopicEntity;
import dev.axonia.events.internal.TopicRepository;
import dev.axonia.events.internal.UpcomingReleasePolicy;
import dev.axonia.events.mother.SectionTypeEntityMother;
import dev.axonia.events.mother.SeriesEntityMother;
import dev.axonia.events.mother.SessionEntityMother;
import dev.axonia.events.mother.TopicEntityMother;
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

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SeriesRepository seriesRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ActionItemRepository actionItemRepository;

    @Autowired
    private ActionItemCheckpointRepository actionItemCheckpointRepository;

    @Autowired
    private SectionTypeRepository sectionTypeRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private SessionListReader sessionListReader;

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
                new SessionBatchLookups(sectionTypeRepository, attendanceRepository, seriesRepository));
        var upcomingReleasePolicy = new UpcomingReleasePolicy(communityAccess, clock);
        var sectionTypeCatalog = new SectionTypeCatalog(sectionTypeRepository);
        var facilitatorResolver = new FacilitatorResolver(communityAccess);
        var sessionCreation = new SessionCreation(upcomingReleasePolicy);
        var actionItemMapper = new ActionItemMapper();
        var actionItemCloser = new ActionItemCloser(actionItemRepository, actionItemMapper, events);
        var actionItemLifecycle = new ActionItemLifecycle(actionItemRepository, actionItemCheckpointRepository,
                actionItemCloser);
        var checkpointDecisionPass = new CheckpointDecisionPass(actionItemRepository, actionItemLifecycle);
        sessionService = new SessionService(sessionRepository, seriesRepository, topicRepository, actionItemRepository,
                checkpointDecisionPass, mapper, sessionListReader, sessionCreation, sessionAuthorizer, communityAccess,
                currentUser, organizationContext, events, clock, upcomingReleasePolicy,
                new SessionInstant(seriesRepository), new UsageCounters(new SimpleMeterRegistry()), sectionTypeCatalog,
                facilitatorResolver);
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
        lenient().when(attendanceRepository.countBySessionIdIn(any(), any())).thenReturn(List.of());
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

        seriesId = seriesRepository
                .save(SeriesEntityMother.any().organizationId("default").communityId(COMMUNITY).timeZone("UTC").build())
                .getId();

        var heldAt = NOW.minus(1, ChronoUnit.DAYS);
        sessionId = sessionRepository.save(SessionEntityMother.any().organizationId("default").seriesId(seriesId)
                .communityId(COMMUNITY).status("HELD").scheduledAt(heldAt)
                .occurrenceDate(heldAt.atZone(ZoneOffset.UTC).toLocalDate()).sections(List.of()).build()).getId();

        var elsewhereAt = NOW.minus(2, ChronoUnit.DAYS);
        otherSessionId = sessionRepository
                .save(SessionEntityMother.any().organizationId("default").seriesId(seriesId).communityId(COMMUNITY)
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

        var tagRefs = tags.stream().map(t -> ActionItemTagRef.builder().tag(t).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return actionItemRepository
                .save(ActionItemEntity.builder().organizationId("default").communityId(communityId)
                        .title("Do the thing").description("").raisedByUserId("carol").assigneeUserId(assignee)
                        .checkpointSessionId(checkpointSessionId).outcome(ActionItemOutcome.OPEN).tags(tagRefs).build())
                .getId();
    }

    private Long slottedTopic(Long ofSessionId, TopicKind kind) {

        var topicId = topicRepository
                .save(TopicEntityMother.any().kind(kind).communityId(COMMUNITY).status(TopicStatus.SCHEDULED).build())
                .getId();
        var sectionTypeId = sectionTypeRepository.save(SectionTypeEntityMother.any().organizationId("default").build())
                .getId();
        var session = sessionRepository.findByOrganizationIdAndId("default", ofSessionId).orElseThrow();
        var slot = SlotEntity.builder().title("Talk").topicId(topicId).durationMinutes(15).presenters(Set.of()).build();
        var section = SectionEntity.builder().sectionTypeId(sectionTypeId).title("Talks")
                .slots(new ArrayList<>(List.of(slot))).build();
        sessionRepository.save(session.toBuilder().sections(new ArrayList<>(List.of(section))).build());
        return topicId;
    }

    private ActionItemEntity reload(Long id) {

        return actionItemRepository.findByOrganizationIdAndId("default", id).orElseThrow();
    }

    private TopicEntity reloadTopic(Long id) {

        return topicRepository.findByOrganizationIdAndId("default", id).orElseThrow();
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
