package dev.axonia.events.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
import dev.axonia.events.adapter.out.identity.UserDirectoryPresentPeople;
import dev.axonia.events.adapter.out.persistence.JdbcSectionTypeStore;
import dev.axonia.events.adapter.out.persistence.JdbcSeriesStore;
import dev.axonia.events.adapter.out.persistence.JdbcSessionStore;
import dev.axonia.events.adapter.out.persistence.JdbcTopicStore;
import dev.axonia.events.adapter.out.persistence.SectionTypeRepository;
import dev.axonia.events.adapter.out.persistence.SeriesRepository;
import dev.axonia.events.adapter.out.persistence.SessionRepository;
import dev.axonia.events.adapter.out.persistence.TopicRepository;
import dev.axonia.events.application.PresenterResolver;
import dev.axonia.events.application.QueueTopicRequest;
import dev.axonia.events.application.SessionAuthorizer;
import dev.axonia.events.application.SessionBatchLookups;
import dev.axonia.events.application.SessionMapper;
import dev.axonia.events.application.SlotEventPublisher;
import dev.axonia.events.application.SlotService;
import dev.axonia.events.application.TopicAuthorizer;
import dev.axonia.events.application.TopicEvidence;
import dev.axonia.events.application.TopicLifecycle;
import dev.axonia.events.application.TopicMapper;
import dev.axonia.events.application.TopicReadModel;
import dev.axonia.events.application.port.out.AttendanceStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SectionTypeStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.application.port.out.TopicVoteStore;
import dev.axonia.events.application.port.out.UserProfileAccess;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SectionTypeRecordMother;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.events.domain.TopicRecordMother;
import dev.axonia.identity.api.UserDirectory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Through the real stores: a backlog offer queued onto a session hands its speakers to the slot, who presents it is
 * then whatever the slot says, and dropping the slot sends the topic back to the backlog with those presenters.
 */
@ExtendWith(MockitoExtension.class)
class TopicSpeakerRoundTripTest extends CoreJdbcTestSupport {

    private static final long COMMUNITY = 1L;

    private SessionStore sessionStore;
    private TopicStore topicStore;
    private SectionTypeStore sectionTypeStore;
    private JdbcSeriesStore seriesStore;

    @Autowired
    private JdbcTemplate jdbc;

    @Mock
    private AttendanceStore attendanceStore;
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
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TopicVoteStore topicVoteStore;
    @Mock
    private UserProfileAccess userProfileAccess;
    @Mock
    private TopicAuthorizer topicAuthorizer;
    @Mock
    private UserDirectory userDirectory;

    private final Set<String> gone = new HashSet<>();

    private SlotService slotService;
    private TopicReadModel topicReadModel;

    private Long sessionId;
    private Long sectionId;

    @Autowired
    void wireStores(SessionRepository sessionRepository, SeriesRepository seriesRepository,
            TopicRepository topicRepository, SectionTypeRepository sectionTypeRepository) {

        sessionStore = new JdbcSessionStore(sessionRepository);
        seriesStore = new JdbcSeriesStore(seriesRepository);
        topicStore = new JdbcTopicStore(topicRepository);
        sectionTypeStore = new JdbcSectionTypeStore(sectionTypeRepository);
    }

    @BeforeEach
    void wireServices() {

        var mapper = new SessionMapper(communityAccess, messageOutcomePort,
                new SessionBatchLookups(sectionTypeStore, attendanceStore, seriesStore));
        slotService = new SlotService(sessionStore,
                new TopicLifecycle(topicStore, new TopicEvidence(eventPublisher),
                        new UserDirectoryPresentPeople(userDirectory)),
                mapper, sessionAuthorizer, communityAccess, currentUser, organizationContext, eventPublisher,
                new PresenterResolver(communityAccess), new SlotEventPublisher(eventPublisher, topicStore));
        topicReadModel = new TopicReadModel(topicVoteStore, userProfileAccess, topicAuthorizer, new TopicMapper(),
                currentUser, topicStore);

        when(organizationContext.organizationId()).thenReturn("default");
        lenient().when(currentUser.userId()).thenReturn("bob");
        lenient().when(communityAccess.isMember(anyLong(), any())).thenReturn(true);
        lenient().when(communityAccess.isOrganizer(anyLong(), any())).thenReturn(true);
        lenient().when(messageOutcomePort.outcomeFor(any(), any())).thenReturn(MessageOutcome.builder().build());
        lenient().when(attendanceStore.countBySessionIdIn(any(), any())).thenReturn(List.of());
        lenient().when(userProfileAccess.displayName(any(), any())).thenReturn("Alice");
        lenient().when(userDirectory.presentOf(eq("default"), any()))
                .thenAnswer(inv -> inv.<Collection<String>>getArgument(1).stream().filter(id -> !gone.contains(id))
                        .collect(Collectors.toSet()));
    }

    @BeforeEach
    void seedASession() {

        jdbc.update("INSERT INTO community_kind (organization_id, label, created_at, updated_at, version) "
                + "VALUES ('default', 'Guild', now(), now(), 0) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO communities (id, organization_id, slug, name, kind_id, description, status, "
                + "created_at, updated_at, version) SELECT ?,'default','c1','C', k.id,'','ACTIVE', now(), now(), 0 "
                + "FROM community_kind k WHERE k.organization_id='default' AND k.label='Guild' ON CONFLICT DO NOTHING",
                COMMUNITY);
        var sectionTypeId = sectionTypeStore.save(SectionTypeRecordMother.any().organizationId("default").build())
                .getId();
        var at = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
        var session = sessionStore.save(SessionRecordMother.any().organizationId("default").seriesId(null)
                .communityId(COMMUNITY).status("SCHEDULED").scheduledAt(at)
                .sections(new ArrayList<>(List.of(
                        SectionRecord.builder().sectionTypeId(sectionTypeId).title("Talks").slots(List.of()).build())))
                .build());
        sessionId = session.getId();
        sectionId = session.getSections().get(0).getId();
    }

    @Test
    void scheduledTopic_speakersFollowSlotSignUpAndWithdraw() {

        var topicId = topicStore.save(TopicRecordMother.any().organizationId("default").communityId(COMMUNITY)
                .proposerUserId("alice").status(TopicStatus.PROPOSED).build().withSpeakers(List.of("alice"))).getId();

        var queued = slotService.queueTopic(sessionId, sectionId, new QueueTopicRequest(topicId, 20, null, null));
        var slotId = queued.sections().get(0).slots().get(0).id();
        assertThat(read(topicId).speakerUserIds()).containsExactly("alice");
        assertThat(rowsOf(topicId)).isEmpty();

        slotService.signUp(sessionId, sectionId, slotId);
        assertThat(read(topicId).speakerUserIds()).containsExactly("alice", "bob");

        when(currentUser.userId()).thenReturn("alice");
        slotService.withdraw(sessionId, sectionId, slotId);
        assertThat(read(topicId).speakerUserIds()).containsExactly("bob");

        slotService.removeSlot(sessionId, sectionId, slotId);
        var released = read(topicId);
        assertThat(released.status()).isEqualTo(TopicStatus.PROPOSED);
        assertThat(released.kind()).isEqualTo(TopicKind.OFFER);
        assertThat(released.speakerUserIds()).containsExactly("bob");
        assertThat(rowsOf(topicId)).containsExactly("bob");
    }

    @Test
    void releasedTopic_leavesErasedAndDeprovisionedPresentersOnTheSlotOnly() {

        var topicId = topicStore.save(TopicRecordMother.any().organizationId("default").communityId(COMMUNITY)
                .proposerUserId("bob").status(TopicStatus.PROPOSED).build().withSpeakers(List.of("bob"))).getId();
        var queued = slotService.queueTopic(sessionId, sectionId, new QueueTopicRequest(topicId, 20, null, null));
        var slotId = queued.sections().get(0).slots().get(0).id();
        for (var presenter : List.of("erased:alice", "dave")) {
            when(currentUser.userId()).thenReturn(presenter);
            slotService.signUp(sessionId, sectionId, slotId);
        }
        gone.addAll(List.of("erased:alice", "dave"));

        slotService.removeSlot(sessionId, sectionId, slotId);

        assertThat(read(topicId).speakerUserIds()).containsExactly("bob");
        assertThat(rowsOf(topicId)).containsExactly("bob");
    }

    private Topic read(Long topicId) {

        return topicReadModel.single(topicStore.findByOrganizationIdAndId("default", topicId).orElseThrow(), null);
    }

    private List<String> rowsOf(Long topicId) {

        return jdbc.queryForList("SELECT user_id FROM topic_speaker WHERE topic_id = ?", String.class, topicId);
    }
}
