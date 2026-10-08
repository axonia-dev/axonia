package dev.axonia.events;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.communication.MessageOutcomePort;
import dev.axonia.community.ContributionPolicy;
import dev.axonia.community.MemberMatch;
import dev.axonia.events.internal.AttendanceRepository;
import dev.axonia.events.internal.PresenterResolver;
import dev.axonia.events.internal.SectionEntity;
import dev.axonia.events.internal.SectionTypeRepository;
import dev.axonia.events.internal.SeriesRepository;
import dev.axonia.events.internal.SessionAuthorizer;
import dev.axonia.events.internal.SessionBatchLookups;
import dev.axonia.events.internal.SessionEntity;
import dev.axonia.events.internal.SessionMapper;
import dev.axonia.events.internal.SessionRepository;
import dev.axonia.events.internal.SlotEntity;
import dev.axonia.events.internal.SlotEventPublisher;
import dev.axonia.events.internal.SlotPresenterRef;
import dev.axonia.events.internal.TopicEntity;
import dev.axonia.events.internal.TopicLifecycle;
import dev.axonia.events.internal.TopicRepository;
import dev.axonia.events.internal.TopicTagRef;
import dev.axonia.events.mother.SessionEntityMother;
import dev.axonia.events.mother.TopicEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class SlotServiceTest {

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private TopicRepository topicRepository;
    @Mock
    private SeriesRepository seriesRepository;
    @Mock
    private SectionTypeRepository sectionTypeRepository;
    @Mock
    private AttendanceRepository attendanceRepository;
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

    private SlotService sut;

    @BeforeEach
    void wire() {

        var mapper = new SessionMapper(communityAccess, messageOutcomePort,
                new SessionBatchLookups(sectionTypeRepository, attendanceRepository, seriesRepository));
        var topicLifecycle = new TopicLifecycle(topicRepository);
        var presenterResolver = new PresenterResolver(communityAccess);
        var slotEventPublisher = new SlotEventPublisher(eventPublisher, topicRepository);
        sut = new SlotService(sessionRepository, topicLifecycle, mapper, sessionAuthorizer, communityAccess,
                currentUser, organizationContext, eventPublisher, presenterResolver, slotEventPublisher);
    }

    private static SlotEntity slot(Long id, String... presenters) {

        var refs = new HashSet<SlotPresenterRef>();
        for (var p : presenters) {
            refs.add(SlotPresenterRef.builder().userId(p).build());
        }
        return SlotEntity.builder().id(id).presenters(refs).build();
    }

    private static SessionEntity.SessionEntityBuilder sessionWith(SlotEntity... slots) {

        var section = SectionEntity.builder().id(21L).sectionTypeId(1L).slots(new ArrayList<>(List.of(slots))).build();
        return SessionEntityMother.any().id(7L).communityId(1L).sections(new ArrayList<>(List.of(section)));
    }

    private static Slot onlySlot(Session session) {

        return session.sections().get(0).slots().get(0);
    }

    /** Mimics Spring Data JDBC assigning an id to the newly-inserted slot on save. */
    private static SessionEntity withGeneratedSlotId(SessionEntity saved, Long id) {

        var section = saved.getSections().get(0);
        var slots = new ArrayList<>(section.getSlots());
        var lastIndex = slots.size() - 1;
        slots.set(lastIndex, slots.get(lastIndex).toBuilder().id(id).build());
        var sections = new ArrayList<>(saved.getSections());
        sections.set(0, section.toBuilder().slots(slots).build());
        return saved.toBuilder().sections(sections).build();
    }

    @Test
    void addSlot_persistsDuration() {

        // given
        var session = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(sessionRepository.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(session));
        when(communityAccess.isMember(1L, "u1")).thenReturn(true);
        when(currentUser.userId()).thenReturn("u1");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.addSlot(7L, 21L, new AddSlotRequest("My talk", null, 20, null, null));

        // then
        var slot = actual.sections().get(0).slots().get(0);
        assertThat(slot.durationMinutes()).isEqualTo(20);
    }

    @Test
    void addSlot_appendsSlotWithSelfAsPresenter() {

        // given
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.addSlot(7L, 21L, new AddSlotRequest("My talk", null, 15, null, null));

        // then
        assertThat(actual.sections().get(0).slots()).singleElement().satisfies(s -> {
            assertThat(s.title()).isEqualTo("My talk");
            assertThat(s.presenterUserIds()).containsExactly("alice");
        });
    }

    @Test
    void addSlot_notMemberRejected() {

        // given
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("intruder");
        when(communityAccess.isMember(1L, "intruder")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.addSlot(7L, 21L, new AddSlotRequest("x", null, 15, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addSlot_onCancelledRejected() {

        // given
        var entity = sessionWith().status("CANCELLED").build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);

        // then
        assertThatThrownBy(() -> sut.addSlot(7L, 21L, new AddSlotRequest("x", null, 15, null, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_CANCELLED");
    }

    @Test
    void addSlot_sectionNotFound() {

        // given
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);

        // then
        assertThatThrownBy(() -> sut.addSlot(7L, 999L, new AddSlotRequest("x", null, 15, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addSlot_withTopicAttaches() {

        // given
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, true, false));
        when(topicRepository.findByOrganizationIdAndId("default", 50L))
                .thenReturn(Optional.of(TopicEntityMother.any().id(50L).communityId(1L).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.addSlot(7L, 21L, new AddSlotRequest("Talk", 50L, 15, null, null));

        // then
        assertThat(actual.sections().get(0).slots()).singleElement()
                .satisfies(s -> assertThat(s.topicId()).isEqualTo(50L));
        org.mockito.Mockito.verify(topicRepository)
                .save(org.mockito.ArgumentMatchers.argThat(t -> t.getStatus() == TopicStatus.SCHEDULED));
    }

    @Test
    void addSlot_topicNotFound() {

        // given
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, true, false));
        when(topicRepository.findByOrganizationIdAndId("default", 50L)).thenReturn(Optional.empty());

        // then
        assertThatThrownBy(() -> sut.addSlot(7L, 21L, new AddSlotRequest("Talk", 50L, 15, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addSlot_topicCommunityMismatchRejected() {

        // given
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, true, false));
        when(topicRepository.findByOrganizationIdAndId("default", 50L))
                .thenReturn(Optional.of(TopicEntityMother.any().id(50L).communityId(999L).build()));

        // then
        assertThatThrownBy(() -> sut.addSlot(7L, 21L, new AddSlotRequest("Talk", 50L, 15, null, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("TOPIC_COMMUNITY_MISMATCH");
    }

    @Test
    void addSlot_withTopicTagsAndPresenter_publishesSlotPresented() {

        // given — alice adds a slot backed by topic 50 which carries tag "kafka"
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, true, false));
        when(topicRepository.findByOrganizationIdAndId("default", 50L)).thenReturn(Optional.of(TopicEntityMother.any()
                .id(50L).communityId(1L).tags(Set.of(TopicTagRef.builder().tag("kafka").build())).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.addSlot(7L, 21L, new AddSlotRequest("My talk", 50L, 15, null, null));

        // then — SlotPresented published with inherited tags and presenter
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(SlotPresented.class, sp -> {
            assertThat(sp.communityId()).isEqualTo(1L);
            assertThat(sp.presenterUserIds()).containsExactly("alice");
            assertThat(sp.tags()).containsExactly("kafka");
        });
    }

    /**
     * Scheduling a backlog topic is a contribution the community's own policy governs. queueTopic asks; addSlot reaches
     * the same lifecycle transition through attachTopic and did not, so the policy was one endpoint wide.
     */
    @Test
    void addSlotCarryingATopicIdObeysTheQueueTopicsPolicy() {

        // given - a plain member, a policy that forbids queueing, and a backlog topic named by id
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(ContributionPolicy.curated());
        // The policy check runs before the topic is ever looked up, so this lookup is unreachable either way - stubbed
        // anyway (lenient) so the topic's mere absence is never what is standing in for the refusal.
        lenient().when(topicRepository.findByOrganizationIdAndId("default", 50L)).thenReturn(
                Optional.of(TopicEntityMother.any().id(50L).communityId(1L).status(TopicStatus.PROPOSED).build()));

        // when / then - the same refusal queueTopic already gives a plain member under a curated policy; the topic
        // exists and would schedule cleanly, so the refusal proves the write was prevented, not merely unreachable
        assertThatThrownBy(() -> sut.addSlot(7L, 21L, new AddSlotRequest("Talk", 50L, 15, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(topicRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    /** An organizer is always allowed, exactly as in queueTopic. */
    @Test
    void anOrganizerMayStillAttachATopicWhenThePolicyForbidsMembers() {

        // given - an organizer, whose write reaches attachTopic through addSlot rather than queueTopic
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.isMember(1L, "orga")).thenReturn(true);
        when(communityAccess.isOrganizer(1L, "orga")).thenReturn(true);
        when(topicRepository.findByOrganizationIdAndId("default", 50L)).thenReturn(
                Optional.of(TopicEntityMother.any().id(50L).communityId(1L).status(TopicStatus.PROPOSED).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.addSlot(7L, 21L, new AddSlotRequest("Talk", 50L, 15, null, null));

        // then - the topic reaches SCHEDULED regardless of what the policy says, because the caller is an organizer
        assertThat(actual.sections().get(0).slots()).singleElement()
                .satisfies(s -> assertThat(s.topicId()).isEqualTo(50L));
        verify(topicRepository).save(argThat(t -> t.getStatus() == TopicStatus.SCHEDULED));
    }

    /** A slot with no topic is unaffected: the policy governs queueing, not slot creation. */
    @Test
    void addSlotWithoutATopicIsUnaffectedByTheQueueTopicsPolicy() {

        // given - the slot carries no topicId, so it never reaches the queue-topics policy at all
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.addSlot(7L, 21L, new AddSlotRequest("Talk", null, 15, null, null));

        // then - the write succeeds without ever consulting isOrganizer or the contribution policy
        assertThat(actual.sections().get(0).slots()).singleElement().satisfies(s -> assertThat(s.topicId()).isNull());
    }

    @Test
    void signUp_addsCurrentUserAsPresenter() {

        // given
        var entity = sessionWith(slot(11L)).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.signUp(7L, 21L, 11L);

        // then
        assertThat(actual.sections().get(0).slots().get(0).presenterUserIds()).containsExactly("alice");
    }

    @Test
    void signUp_duplicateRejected() {

        // given
        var entity = sessionWith(slot(11L, "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);

        // then
        assertThatThrownBy(() -> sut.signUp(7L, 21L, 11L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("ALREADY_PRESENTING");
    }

    @Test
    void signUp_notMemberRejected() {

        // given
        var entity = sessionWith(slot(11L)).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("intruder");
        when(communityAccess.isMember(1L, "intruder")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.signUp(7L, 21L, 11L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void signUp_onCancelledRejected() {

        // given
        var entity = sessionWith(slot(11L)).status("CANCELLED").build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);

        // then
        assertThatThrownBy(() -> sut.signUp(7L, 21L, 11L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SESSION_CANCELLED");
    }

    @Test
    void signUp_slotNotFound() {

        // given
        var entity = sessionWith(slot(11L)).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);

        // then
        assertThatThrownBy(() -> sut.signUp(7L, 21L, 999L)).isInstanceOf(NotFoundException.class);
    }

    private static SlotEntity slotWithTopic(Long id, Long topicId, String... presenters) {

        return slot(id, presenters).toBuilder().topicId(topicId).build();
    }

    @Test
    void withdraw_keepsAdhocSlotOpen_whenLastPresenterLeaves() {

        // given
        var entity = sessionWith(slot(11L, "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.withdraw(7L, 21L, 11L);

        // then
        var slots = actual.sections().get(0).slots();
        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).presenterUserIds()).isEmpty();
    }

    @Test
    void withdraw_keepsOpenSlotWhenTopicPresent() {

        // given
        var entity = sessionWith(slotWithTopic(11L, 50L, "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.withdraw(7L, 21L, 11L);

        // then
        assertThat(actual.sections().get(0).slots()).singleElement().satisfies(s -> {
            assertThat(s.presenterUserIds()).isEmpty();
            assertThat(s.topicId()).isEqualTo(50L);
        });
    }

    @Test
    void withdraw_keepsSlotWhenOtherPresentersRemain() {

        // given
        var entity = sessionWith(slot(11L, "alice", "bob")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.withdraw(7L, 21L, 11L);

        // then
        assertThat(actual.sections().get(0).slots().get(0).presenterUserIds()).containsExactly("bob");
    }

    @Test
    void editSlot_updatesSlot() {

        // given
        var entity = sessionWith(slot(11L, "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Renamed", null, "notes", null, null));

        // then
        assertThat(actual.sections().get(0).slots()).singleElement().satisfies(s -> {
            assertThat(s.title()).isEqualTo("Renamed");
            assertThat(s.notes()).isEqualTo("notes");
        });
    }

    @Test
    void editSlot_updatesDuration() {

        // given
        var entity = sessionWith(slot(11L, "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Renamed", null, null, 30, null));

        // then
        assertThat(actual.sections().get(0).slots()).singleElement()
                .satisfies(s -> assertThat(s.durationMinutes()).isEqualTo(30));
    }

    @Test
    void editSlot_slotNotFound() {

        // given
        var entity = sessionWith(slot(11L, "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));

        // then
        assertThatThrownBy(() -> sut.editSlot(7L, 21L, 999L, new EditSlotRequest("x", null, null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void removeSlot_removesSlot() {

        // given
        var entity = sessionWith(slot(11L, "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.removeSlot(7L, 21L, 11L);

        // then - the user-initiated removal runs the schedule-edit authorization
        assertThat(actual.sections().get(0).slots()).isEmpty();
        verify(sessionAuthorizer).requireSlotOwnerOrManager(eq("default"), eq(entity), any(SlotEntity.class),
                eq("alice"));
    }

    @Test
    void removePresenter_organizerRemovesNamedUser() {

        // given
        var entity = sessionWith(slot(11L, "bob", "carol")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.removePresenter(7L, 21L, 11L, "bob");

        // then
        assertThat(actual.sections().get(0).slots().get(0).presenterUserIds()).containsExactly("carol");
    }

    @Test
    void removePresenter_keepsEmptyTopiclessSlotOpen() {

        // given
        var entity = sessionWith(slot(11L, "bob")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.removePresenter(7L, 21L, 11L, "bob");

        // then
        var slots = actual.sections().get(0).slots();
        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).presenterUserIds()).isEmpty();
        org.mockito.Mockito.verify(sessionAuthorizer).requireSlotOwnerOrManager(
                org.mockito.ArgumentMatchers.eq("default"), org.mockito.ArgumentMatchers.eq(entity),
                org.mockito.ArgumentMatchers.any(dev.axonia.events.internal.SlotEntity.class),
                org.mockito.ArgumentMatchers.eq("orga"));
    }

    @Test
    void reorderSlots_reordersByGivenIds() {

        // given
        var section = SectionEntity.builder().id(21L).sectionTypeId(1L)
                .slots(new ArrayList<>(List.of(slot(101L), slot(102L)))).build();
        var session = SessionEntityMother.any().id(7L).communityId(1L).sections(new ArrayList<>(List.of(section)))
                .build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(sessionRepository.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(session));
        when(currentUser.userId()).thenReturn("org");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.reorderSlots(7L, 21L, new ReorderSlotsRequest(List.of(102L, 101L)));

        // then
        var ids = actual.sections().get(0).slots().stream().map(Slot::id).toList();
        assertThat(ids).containsExactly(102L, 101L);
        org.mockito.Mockito.verify(sessionAuthorizer).requireOrganizer(session, "org");
    }

    @Test
    void reorderSlots_nonOrganizerRejected() {

        // given
        var section = SectionEntity.builder().id(21L).sectionTypeId(1L)
                .slots(new ArrayList<>(List.of(slot(101L), slot(102L)))).build();
        var session = SessionEntityMother.any().id(7L).communityId(1L).sections(new ArrayList<>(List.of(section)))
                .build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(sessionRepository.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(session));
        when(currentUser.userId()).thenReturn("intruder");
        org.mockito.Mockito.doThrow(new ForbiddenException("forbidden")).when(sessionAuthorizer)
                .requireOrganizer(session, "intruder");

        // then
        assertThatThrownBy(() -> sut.reorderSlots(7L, 21L, new ReorderSlotsRequest(List.of(102L, 101L))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void reorderSlots_mismatchRejected() {

        // given
        var section = SectionEntity.builder().id(21L).sectionTypeId(1L)
                .slots(new ArrayList<>(List.of(slot(101L), slot(102L)))).build();
        var session = SessionEntityMother.any().id(7L).communityId(1L).sections(new ArrayList<>(List.of(section)))
                .build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(sessionRepository.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(session));
        when(currentUser.userId()).thenReturn("org");

        // then
        assertThatThrownBy(() -> sut.reorderSlots(7L, 21L, new ReorderSlotsRequest(List.of(101L, 999L))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SLOT_ORDER_MISMATCH");
    }

    @Test
    void queueTopic_createsOpenSlotAndSchedulesTopic() {

        // given
        var entity = sessionWith().build(); // section 21L, no slots
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(true);
        when(topicRepository.findByOrganizationIdAndId("default", 99L))
                .thenReturn(Optional.of(TopicEntity.builder().id(99L).organizationId("default").communityId(1L)
                        .title("Flaky E2E").status(TopicStatus.PROPOSED).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.queueTopic(7L, 21L, new QueueTopicRequest(99L, 20, null, null));

        // then
        var slot = actual.sections().get(0).slots().get(0);
        assertThat(slot.presenterUserIds()).isEmpty();
        assertThat(slot.topicId()).isEqualTo(99L);
        assertThat(slot.title()).isEqualTo("Flaky E2E"); // defaults to the topic title
        verify(topicRepository).save(argThat(t -> t.getStatus() == TopicStatus.SCHEDULED));
    }

    @Test
    void removeSlot_revertsAttachedTopicToProposed() {

        // given
        var slot = SlotEntity.builder().id(11L).topicId(99L).presenters(new HashSet<>()).build();
        var entity = sessionWith(slot).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(topicRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(TopicEntity.builder()
                .id(99L).organizationId("default").communityId(1L).status(TopicStatus.SCHEDULED).build()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.removeSlot(7L, 21L, 11L);

        // then
        verify(topicRepository).save(argThat(t -> t.getStatus() == TopicStatus.PROPOSED));
    }

    @Test
    void removeSlot_doesNotRevertDoneTopic() {

        // given
        var slot = SlotEntity.builder().id(11L).topicId(99L).presenters(new HashSet<>()).build();
        var entity = sessionWith(slot).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(topicRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(TopicEntity.builder()
                .id(99L).organizationId("default").communityId(1L).status(TopicStatus.DONE).build()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.removeSlot(7L, 21L, 11L);

        // then
        verify(topicRepository, never()).save(any());
    }

    @Test
    void editSlot_detachingTopicRevertsItToProposed() {

        // given
        var slot = SlotEntity.builder().id(11L).topicId(99L).presenters(new HashSet<>()).build();
        var entity = sessionWith(slot).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(topicRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(TopicEntity.builder()
                .id(99L).organizationId("default").communityId(1L).status(TopicStatus.SCHEDULED).build()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Renamed", null, null, null, null));

        // then
        verify(topicRepository).save(argThat(t -> t.getStatus() == TopicStatus.PROPOSED));
    }

    @Test
    void editSlot_keepingSameTopicDoesNotRelease() {

        // given - 9001, deliberately outside Long's -128..127 autobox cache: the two occurrences below are separately
        // boxed, distinct objects, so this proves the equality check really uses .equals, not reference identity
        var slot = SlotEntity.builder().id(11L).topicId(9001L).presenters(new HashSet<>()).build();
        var entity = sessionWith(slot).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(topicRepository.findByOrganizationIdAndId("default", 9001L)).thenReturn(Optional.of(TopicEntity.builder()
                .id(9001L).organizationId("default").communityId(1L).status(TopicStatus.SCHEDULED).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - alice is neither an organizer nor stubbed as a member: re-attaching the SAME topic must not reach the
        // queue-topics policy at all, since nothing about the topic's attachment is changing
        sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Renamed", 9001L, null, null, null));

        // then
        // re-attaching the same topic must not revert it to PROPOSED
        verify(topicRepository, never()).save(argThat(t -> t.getStatus() == TopicStatus.PROPOSED));
    }

    /**
     * The unchanged-topic pass-through must not become a blanket exemption for editSlot: swapping a slot's SCHEDULED
     * topic 99 for a different backlog topic 50 is a genuine new act of queueing, and must obey the policy exactly as
     * addSlot and queueTopic already do.
     */
    @Test
    void editSlot_swappingToADifferentTopicObeysTheQueueTopicsPolicy() {

        // given - a plain member, a curated policy, and a slot currently carrying a different topic
        var slot = SlotEntity.builder().id(11L).topicId(99L).presenters(new HashSet<>()).build();
        var entity = sessionWith(slot).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(ContributionPolicy.curated());

        // when / then - carrying a DIFFERENT topicId (50, not the slot's existing 99) still reaches the guard
        assertThatThrownBy(() -> sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Renamed", 50L, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(topicRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void editSlot_memberWithManageScheduleAllowed() {

        // given — "alice" is NOT a presenter; she holds canManageSchedule
        var entity = sessionWith(slot(11L, "other")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        // SessionAuthorizer is mocked; simulate it passing (default: no throw)
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, true));
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Wire the real SessionAuthorizer so the policy check actually runs
        var realAuthorizer = new SessionAuthorizer(communityAccess, topicRepository);
        sut = new SlotService(sessionRepository, new dev.axonia.events.internal.TopicLifecycle(topicRepository),
                new dev.axonia.events.internal.SessionMapper(communityAccess, messageOutcomePort,
                        new dev.axonia.events.internal.SessionBatchLookups(sectionTypeRepository, attendanceRepository,
                                seriesRepository)),
                realAuthorizer, communityAccess, currentUser, organizationContext, eventPublisher,
                new PresenterResolver(communityAccess), new SlotEventPublisher(eventPublisher, topicRepository));

        // when
        var actual = sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Renamed", null, null, null, null));

        // then
        assertThat(actual.sections().get(0).slots()).singleElement()
                .satisfies(s -> assertThat(s.title()).isEqualTo("Renamed"));
    }

    @Test
    void editSlot_plainMemberRejected() {

        // given — "stranger" is NOT a presenter and has no canManageSchedule
        var entity = sessionWith(slot(11L, "other")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("stranger");
        when(communityAccess.isOrganizer(1L, "stranger")).thenReturn(false);
        // curated() has canManageSchedule=false so isMember is never reached
        when(communityAccess.contributionPolicy(1L)).thenReturn(ContributionPolicy.curated());
        // Wire the real SessionAuthorizer so the policy check actually runs
        var realAuthorizer = new SessionAuthorizer(communityAccess, topicRepository);
        sut = new SlotService(sessionRepository, new dev.axonia.events.internal.TopicLifecycle(topicRepository),
                new dev.axonia.events.internal.SessionMapper(communityAccess, messageOutcomePort,
                        new dev.axonia.events.internal.SessionBatchLookups(sectionTypeRepository, attendanceRepository,
                                seriesRepository)),
                realAuthorizer, communityAccess, currentUser, organizationContext, eventPublisher,
                new PresenterResolver(communityAccess), new SlotEventPublisher(eventPublisher, topicRepository));

        // then
        assertThatThrownBy(() -> sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Renamed", null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void queueTopic_memberBlockedWhenCurated() {

        // given
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("intruder");
        when(communityAccess.isOrganizer(1L, "intruder")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(ContributionPolicy.curated());

        // then
        assertThatThrownBy(() -> sut.queueTopic(7L, 21L, new QueueTopicRequest(99L, 20, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void queueTopic_memberAllowedWhenPolicyOpen() {

        // given
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, true, false));
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(topicRepository.findByOrganizationIdAndId("default", 99L))
                .thenReturn(Optional.of(TopicEntity.builder().id(99L).organizationId("default").communityId(1L)
                        .title("Flaky E2E").status(TopicStatus.PROPOSED).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.queueTopic(7L, 21L, new QueueTopicRequest(99L, 20, null, null));

        // then
        assertThat(actual.sections().get(0).slots().get(0).presenterUserIds()).isEmpty();
    }

    @Test
    void queueTopic_nonMemberBlockedEvenWhenPolicyOpen() {

        // given
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("stranger");
        when(communityAccess.isOrganizer(1L, "stranger")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, true, false));
        when(communityAccess.isMember(1L, "stranger")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.queueTopic(7L, 21L, new QueueTopicRequest(99L, 20, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addSlot_persistsNotes() {

        // given
        var session = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(sessionRepository.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(session));
        when(communityAccess.isMember(1L, "u1")).thenReturn(true);
        when(currentUser.userId()).thenReturn("u1");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.addSlot(7L, 21L, new AddSlotRequest("My talk", null, 20, "An abstract", null));

        // then
        assertThat(actual.sections().get(0).slots().get(0).notes()).isEqualTo("An abstract");
    }

    @Test
    void queueTopic_defaultsNotesToTopicDescription_whenOmitted() {

        // given
        var session = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(sessionRepository.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(session));
        when(communityAccess.isOrganizer(1L, "u1")).thenReturn(true);
        when(currentUser.userId()).thenReturn("u1");
        when(topicRepository.findByOrganizationIdAndId("t1", 5L))
                .thenReturn(Optional.of(TopicEntity.builder().id(5L).organizationId("t1").communityId(1L).title("T")
                        .description("Topic desc").status(TopicStatus.PROPOSED).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.queueTopic(7L, 21L, new QueueTopicRequest(5L, 20, null, null));

        // then
        assertThat(actual.sections().get(0).slots().get(0).notes()).isEqualTo("Topic desc");
    }

    @Test
    void queueTopic_usesExplicitNotesWhenProvided() {

        // given
        var session = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(sessionRepository.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(session));
        when(communityAccess.isOrganizer(1L, "u1")).thenReturn(true);
        when(currentUser.userId()).thenReturn("u1");
        when(topicRepository.findByOrganizationIdAndId("t1", 5L))
                .thenReturn(Optional.of(TopicEntity.builder().id(5L).organizationId("t1").communityId(1L).title("T")
                        .description("Topic desc").status(TopicStatus.PROPOSED).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.queueTopic(7L, 21L, new QueueTopicRequest(5L, 20, null, "Explicit notes"));

        // then
        assertThat(actual.sections().get(0).slots().get(0).notes()).isEqualTo("Explicit notes");
    }

    @Test
    void addSlot_onSkippedRejected() {

        var entity = sessionWith().status("SKIPPED").build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(sessionRepository.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(entity));
        when(communityAccess.isMember(1L, "u1")).thenReturn(true);
        when(currentUser.userId()).thenReturn("u1");

        assertThatThrownBy(() -> sut.addSlot(7L, 21L, new AddSlotRequest("x", null, 15, null, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_SKIPPED");
    }

    @Test
    void queueTopic_usesExplicitTitleWhenProvided() {

        // given — explicit title overrides the topic's title (line 85 non-null branch)
        var entity = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(true);
        when(topicRepository.findByOrganizationIdAndId("default", 99L))
                .thenReturn(Optional.of(TopicEntity.builder().id(99L).organizationId("default").communityId(1L)
                        .title("Topic Title").status(TopicStatus.PROPOSED).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.queueTopic(7L, 21L, new QueueTopicRequest(99L, 20, "Custom Title", null));

        // then — slot title comes from the request, not the topic
        assertThat(actual.sections().get(0).slots().get(0).title()).isEqualTo("Custom Title");
    }

    @Test
    void signUp_onSkippedRejected() {

        var entity = sessionWith(slot(11L)).status("SKIPPED").build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(sessionRepository.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(entity));
        when(communityAccess.isMember(1L, "u1")).thenReturn(true);
        when(currentUser.userId()).thenReturn("u1");

        assertThatThrownBy(() -> sut.signUp(7L, 21L, 11L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SESSION_SKIPPED");
    }

    // ── slot-presented / slot-cleared event tests ──────────────────────────────

    @Test
    void fillSlot_publishesSlotPresented_withTopicTags() {

        // given — a slot backed by topic 99 with tag "kafka"; alice signs up
        var entity = sessionWith(slotWithTopic(11L, 99L)).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(topicRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(TopicEntityMother.any()
                .id(99L).communityId(1L).tags(Set.of(TopicTagRef.builder().tag("kafka").build())).build()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.signUp(7L, 21L, 11L);

        // then — SlotPresented published with inherited tags and presenter
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(SlotPresented.class, sp -> {
            assertThat(sp.slotId()).isEqualTo(11L);
            assertThat(sp.communityId()).isEqualTo(1L);
            assertThat(sp.presenterUserIds()).contains("alice");
            assertThat(sp.tags()).contains("kafka");
        });
    }

    @Test
    void fillSlot_publishesSlotCleared_whenTopicHasNoTags() {

        // given — slot backed by topic 99, but topic has null tags → resolveTopicTags returns [] → SlotCleared
        // this covers the tags.isEmpty() == true branch at line 236 in publishSlotEvent
        var entity = sessionWith(slotWithTopic(11L, 99L)).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(topicRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional
                .of(TopicEntity.builder().id(99L).organizationId("default").communityId(1L).tags(null).build()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when — alice signs up; topic has no tags → event should be SlotCleared, not SlotPresented
        sut.signUp(7L, 21L, 11L);

        // then
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(SlotCleared.class);
    }

    @Test
    void clearPresenters_publishesSlotCleared() {

        // given — alice is the sole presenter; she withdraws
        var entity = sessionWith(slot(11L, "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        sut.withdraw(7L, 21L, 11L);

        // then — SlotCleared published
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(SlotCleared.class, sc -> {
            assertThat(sc.slotId()).isEqualTo(11L);
            assertThat(sc.communityId()).isEqualTo(1L);
        });
    }

    @Test
    void slotWithoutPresenters_publishesSlotCleared() {

        // given — slot has topic but no presenters; add a topic via editSlot with no topic change
        // We test removeSlot which should always publish SlotCleared
        var slot = SlotEntity.builder().id(11L).topicId(99L).presenters(new HashSet<>()).build();
        var entity = sessionWith(slot).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(topicRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(
                TopicEntity.builder().id(99L).organizationId("default").communityId(1L).status(TopicStatus.SCHEDULED)
                        .tags(Set.of(TopicTagRef.builder().tag("kafka").build())).build()));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when — remove slot → always clears
        sut.removeSlot(7L, 21L, 11L);

        // then
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOfSatisfying(SlotCleared.class, sc -> {
            assertThat(sc.slotId()).isEqualTo(11L);
            assertThat(sc.communityId()).isEqualTo(1L);
        });
    }

    // ── recordSlot: backfilling a slot presented by named people ───────────────
    // Authorization itself (organizer / member-with-canManageSchedule / neither) is unit-tested against the real
    // branch logic in SessionAuthorizerTest#requireScheduleManager_*; here sessionAuthorizer is the class mock and
    // simply passes (default no-op) or is told to throw, matching this file's existing convention for reorderSlots.

    @Test
    void recordSlot_assignsTheNamedPresenters_andNeverTheCaller() {

        // given
        var session = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(session));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));
        when(sessionRepository.save(any())).thenAnswer(inv -> withGeneratedSlotId(inv.getArgument(0), 55L));

        // when
        var recorded = sut.recordSlot(7L, 21L,
                new RecordSlotRequest("Kafka 101", 15, null, List.of("Alice Dupont"), null));

        // then
        var slot = onlySlot(recorded.session());
        assertThat(slot.presenterUserIds()).containsExactly("alice-id").doesNotContain("orga");
        assertThat(recorded.presenters().assigned()).containsExactly("Alice Dupont");
        assertThat(recorded.slotId()).isNotNull();
    }

    @Test
    void recordSlot_withNoPresenters_createsAnOpenSlot() {

        // given
        var session = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(session));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var recorded = sut.recordSlot(7L, 21L, new RecordSlotRequest("Round table", 20, null, List.of(), null));

        // then
        assertThat(onlySlot(recorded.session()).presenterUserIds()).isEmpty();
        assertThat(recorded.presenters()).isEqualTo(PresenterResolution.empty());
    }

    @Test
    void recordSlot_withANullPresenterInTheArray_dropsItRatherThanFailingTheWrite() {

        // given - an exported row whose speaker column was empty: a JSON array carrying a null element
        var session = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(session));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));
        when(sessionRepository.save(any())).thenAnswer(inv -> withGeneratedSlotId(inv.getArgument(0), 55L));

        // when - the same tolerance tags already have; List.copyOf here would have made this a 500
        var recorded = sut.recordSlot(7L, 21L, new RecordSlotRequest("Kafka 101", 15, null,
                Arrays.asList("Alice Dupont", null, "  "), List.of("kafka")));

        // then - the null and the blank are dropped, and they are not reported as names nobody could place
        assertThat(onlySlot(recorded.session()).presenterUserIds()).containsExactly("alice-id");
        assertThat(recorded.presenters().assigned()).containsExactly("Alice Dupont");
        assertThat(recorded.presenters().unmatched()).isEmpty();
        assertThat(recorded.presenters().ambiguous()).isEmpty();
    }

    @Test
    void recordSlot_reportsUnmatchedAndAmbiguousNames_andStillCreatesTheSlot() {

        // given
        var session = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(session));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));
        when(communityAccess.resolveMember(1L, "J. Smith")).thenReturn(new MemberMatch.NotFound());
        when(communityAccess.resolveMember(1L, "Bob")).thenReturn(new MemberMatch.Ambiguous(3));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var recorded = sut.recordSlot(7L, 21L,
                new RecordSlotRequest("Kafka 101", 15, null, List.of("Alice Dupont", "J. Smith", "Bob"), null));

        // then
        assertThat(recorded.presenters().assigned()).containsExactly("Alice Dupont");
        assertThat(recorded.presenters().unmatched()).containsExactly("J. Smith");
        assertThat(recorded.presenters().ambiguous()).containsExactly("Bob");
        assertThat(onlySlot(recorded.session()).presenterUserIds()).containsExactly("alice-id");
    }

    @Test
    void recordSlot_dedupsByUserId_whenTwoDifferentQueriesResolveToTheSameMember() {

        // given - dirty migration data spells the same presenter two different ways
        var session = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(session));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));
        when(communityAccess.resolveMember(1L, "alice@corp.com")).thenReturn(new MemberMatch.Matched("alice-id"));
        when(sessionRepository.save(any())).thenAnswer(inv -> withGeneratedSlotId(inv.getArgument(0), 55L));

        // when
        var recorded = sut.recordSlot(7L, 21L,
                new RecordSlotRequest("Kafka 101", 15, null, List.of("Alice Dupont", "alice@corp.com"), null));

        // then - both original references placed, but the persisted presenter set carries the member once
        assertThat(recorded.presenters().assigned()).containsExactly("Alice Dupont", "alice@corp.com");
        assertThat(onlySlot(recorded.session()).presenterUserIds()).containsExactly("alice-id");
    }

    @Test
    void recordSlot_onAHeldSession_succeeds_becauseThatIsThePointOfBackfill() {

        // given - a session scheduled in the past is "held", derived rather than stored
        var session = sessionWith().scheduledAt(java.time.Instant.parse("2020-01-01T18:00:00Z")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(session));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> withGeneratedSlotId(inv.getArgument(0), 55L));

        // when
        var recorded = sut.recordSlot(7L, 21L, new RecordSlotRequest("Kafka 101", 15, null, List.of(), null));

        // then
        assertThat(recorded.slotId()).isNotNull();
    }

    @Test
    void recordSlot_requiresScheduleManagementRights() {

        // given
        var session = sessionWith().build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(session));
        when(currentUser.userId()).thenReturn("stranger");
        doThrow(new ForbiddenException("Recording a slot requires schedule-management rights")).when(sessionAuthorizer)
                .requireScheduleManager(1L, "stranger");

        // then
        assertThatThrownBy(() -> sut.recordSlot(7L, 21L, new RecordSlotRequest("Kafka 101", 15, null, List.of(), null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void recordSlot_onACancelledSession_isRejected() {

        // given
        var session = sessionWith().status("CANCELLED").build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(session));

        // then
        assertThatThrownBy(() -> sut.recordSlot(7L, 21L, new RecordSlotRequest("Kafka 101", 15, null, List.of(), null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_CANCELLED");
    }

    @Test
    void recordSlot_onASkippedSession_isRejected() {

        // given
        var session = sessionWith().status("SKIPPED").build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(session));

        // then
        assertThatThrownBy(() -> sut.recordSlot(7L, 21L, new RecordSlotRequest("Kafka 101", 15, null, List.of(), null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SESSION_SKIPPED");
    }

    // ── addPresenter: assigning a resolved member to an existing slot ──────────

    @Test
    void addPresenter_assignsAResolvedMemberToAnExistingSlot() {

        // given
        var entity = sessionWith(slot(11L)).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = sut.addPresenter(7L, 21L, 11L, "Alice Dupont");

        // then
        assertThat(result.assigned()).isTrue();
        assertThat(result.reason()).isNull();
        assertThat(result.candidates()).isZero();
        assertThat(onlySlot(result.session()).presenterUserIds()).containsExactly("alice-id");
    }

    @Test
    void addPresenter_twice_isANoOp_becauseThePresenterSetIsKeyedByUser() {

        // given - the second call must see the slot as the first call left it, so track state across saves
        var state = new SessionEntity[]{sessionWith(slot(11L)).build()};
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenAnswer(inv -> Optional.of(state[0]));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));
        when(sessionRepository.save(any())).thenAnswer(inv -> {
            state[0] = inv.getArgument(0);
            return state[0];
        });

        // when - assign the same presenter twice
        sut.addPresenter(7L, 21L, 11L, "Alice Dupont");
        var result = sut.addPresenter(7L, 21L, 11L, "Alice Dupont");

        // then - the second call still succeeds and the presenter set still carries the member exactly once
        assertThat(result.assigned()).isTrue();
        assertThat(onlySlot(result.session()).presenterUserIds()).containsExactly("alice-id");
        verify(sessionRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void addPresenter_withAnUnresolvableName_assignsNobody_andSaysWhy() {

        // given
        var entity = sessionWith(slot(11L)).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "J. Smith")).thenReturn(new MemberMatch.NotFound());

        // when
        var result = sut.addPresenter(7L, 21L, 11L, "J. Smith");

        // then - nobody matched, so there is no candidate count to report
        assertThat(result.assigned()).isFalse();
        assertThat(result.reason()).isEqualTo(PresenterAssignment.NOT_FOUND);
        assertThat(result.candidates()).isZero();
        assertThat(onlySlot(result.session()).presenterUserIds()).isEmpty();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void addPresenter_withAnAmbiguousName_assignsNobody_andSaysHowManyMembersItMatched() {

        // given - three members answer to "Bob"
        var entity = sessionWith(slot(11L)).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "Bob")).thenReturn(new MemberMatch.Ambiguous(3));

        // when
        var result = sut.addPresenter(7L, 21L, 11L, "Bob");

        // then - the count survives: a caller told only "ambiguous" cannot tell two namesakes from a vague query
        assertThat(result.assigned()).isFalse();
        assertThat(result.reason()).isEqualTo(PresenterAssignment.AMBIGUOUS);
        assertThat(result.candidates()).isEqualTo(3);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void addPresenter_requiresSlotOwnershipOrScheduleManagementRights() {

        // given
        var entity = sessionWith(slot(11L)).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("stranger");
        doThrow(new ForbiddenException("Editing the schedule requires slot ownership or schedule-management rights"))
                .when(sessionAuthorizer)
                .requireSlotOwnerOrManager(eq("default"), eq(entity), any(SlotEntity.class), eq("stranger"));

        // then
        assertThatThrownBy(() -> sut.addPresenter(7L, 21L, 11L, "Alice Dupont")).isInstanceOf(ForbiddenException.class);
    }
}
