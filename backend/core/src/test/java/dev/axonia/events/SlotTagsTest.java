package dev.axonia.events;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import dev.axonia.events.internal.SlotTagRef;
import dev.axonia.events.internal.TopicLifecycle;
import dev.axonia.events.internal.TopicRepository;
import dev.axonia.events.internal.TopicTagRef;
import dev.axonia.events.mother.SessionEntityMother;
import dev.axonia.events.mother.TopicEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A talk that was never proposed carries its own subject. These tests assert on the published domain event, because
 * {@link SlotPresented} is the whole point: the expertise graph is learned from it, and a slot that publishes nothing
 * teaches it nothing. The invariant under test is that a slot has a topic or its own tags, never both.
 */
@MockitoSettings
class SlotTagsTest {

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

    private static SlotEntity slot(Long id, Long topicId, Set<String> tags, String... presenters) {

        var presenterRefs = new HashSet<SlotPresenterRef>();
        for (var p : presenters) {
            presenterRefs.add(SlotPresenterRef.builder().userId(p).build());
        }
        var tagRefs = tags.stream().map(t -> SlotTagRef.builder().tag(t).build())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return SlotEntity.builder().id(id).topicId(topicId).presenters(presenterRefs).tags(tagRefs).build();
    }

    private static SessionEntity.SessionEntityBuilder sessionWith(SlotEntity... slots) {

        var section = SectionEntity.builder().id(21L).sectionTypeId(1L).slots(new ArrayList<>(List.of(slots))).build();
        return SessionEntityMother.any().id(7L).communityId(1L).sections(new ArrayList<>(List.of(section)));
    }

    /** The session an organizer is backfilling into, with the caller already resolved as its schedule manager. */
    private void organizerOnAnEmptySession() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(sessionWith().build()));
        when(currentUser.userId()).thenReturn("orga");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Object publishedEvent() {

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void aRecordedSlotWithTagsPublishesSlotPresentedWithThoseTags() {

        // given - an organizer backfills a 2023 talk Alice gave, tagged by hand
        organizerOnAnEmptySession();
        when(communityAccess.resolveMember(1L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));

        // when
        sut.recordSlot(7L, 21L,
                new RecordSlotRequest("Kafka 101", 15, null, List.of("Alice Dupont"), List.of("kafka", "streams")));

        // then - the expertise graph finally hears about it
        assertThat(publishedEvent()).isInstanceOfSatisfying(SlotPresented.class, sp -> {
            assertThat(sp.communityId()).isEqualTo(1L);
            assertThat(sp.presenterUserIds()).containsExactly("alice-id");
            assertThat(sp.tags()).containsExactly("kafka", "streams");
        });
    }

    @Test
    void aRecordedSlotWithNoPresentersPublishesSlotCleared() {

        // given - a round table nobody is credited for, however well tagged
        organizerOnAnEmptySession();

        // when
        sut.recordSlot(7L, 21L, new RecordSlotRequest("Round table", 20, null, List.of(), List.of("kafka")));

        // then - tags without a presenter credit nobody
        assertThat(publishedEvent()).isInstanceOf(SlotCleared.class);
    }

    @Test
    void aRecordedSlotWithNoTagsPublishesSlotCleared() {

        // given
        organizerOnAnEmptySession();
        when(communityAccess.resolveMember(1L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));

        // when - a presenter, but no subject at all
        sut.recordSlot(7L, 21L, new RecordSlotRequest("Kafka 101", 15, null, List.of("Alice Dupont"), null));

        // then
        assertThat(publishedEvent()).isInstanceOf(SlotCleared.class);
    }

    @Test
    void aSlotFilledFromATopicTakesItsSubjectFromTheTopic() {

        // given - the slot carries no tags of its own; topic 50 carries "kafka"
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(sessionWith().build()));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, true, false));
        when(topicRepository.findByOrganizationIdAndId("default", 50L)).thenReturn(Optional.of(TopicEntityMother.any()
                .id(50L).communityId(1L).tags(Set.of(TopicTagRef.builder().tag("kafka").build())).build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        // an empty tag list alongside a topic is not "both": there is only one subject, the topic's
        var session = sut.addSlot(7L, 21L, new AddSlotRequest("Kafka 101", 50L, 15, null, List.of()));

        // then - the subject came from the topic, and the slot stored none of its own
        assertThat(session.sections().get(0).slots().get(0).tags()).isEmpty();
        assertThat(publishedEvent()).isInstanceOfSatisfying(SlotPresented.class,
                sp -> assertThat(sp.tags()).containsExactly("kafka"));
    }

    /**
     * Deliberately not named "re-tagging a topic updates the talk it filled": it does not, on its own. Nothing
     * republishes a slot when its topic is re-tagged, so this pins only what the code does - a slot that publishes
     * reads its topic's tags as they are at that moment, so the correction rides along on the next write to the slot.
     */
    @Test
    void republishingASlotReadsItsTopicsTagsAsTheyAreNow() {

        // given - a slot filled from topic 99 and presented by alice; the topic has since been re-tagged
        var entity = sessionWith(slot(11L, 99L, Set.of(), "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(topicRepository.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.of(TopicEntityMother
                .any().id(99L).communityId(1L).tags(new LinkedHashSet<>(List
                        .of(TopicTagRef.builder().tag("kafka").build(), TopicTagRef.builder().tag("streams").build())))
                .build()));
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - the slot is written to (here: an unrelated edit), which is what republishes it
        sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Kafka 101", 99L, null, null, null));

        // then - the event carries the topic's current tags, not a copy taken when the slot was filled
        assertThat(publishedEvent()).isInstanceOfSatisfying(SlotPresented.class,
                sp -> assertThat(sp.tags()).containsExactlyInAnyOrder("kafka", "streams"));
    }

    @Test
    void aSlotWithBothATopicAndTagsIsRefused() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(sessionWith().build()));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);

        // then - two subjects, and no rule for choosing: refuse rather than silently drop one
        assertThatThrownBy(() -> sut.addSlot(7L, 21L, new AddSlotRequest("Kafka 101", 50L, 15, null, List.of("kafka"))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SLOT_TOPIC_AND_TAGS");
        verify(sessionRepository, never()).save(any());
        verify(topicRepository, never()).save(any());
    }

    @Test
    void editingASlotWithBothATopicAndTagsIsRefused() {

        // given
        var entity = sessionWith(slot(11L, null, Set.of(), "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");

        // then
        assertThatThrownBy(
                () -> sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Kafka 101", 50L, null, null, List.of("kafka"))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SLOT_TOPIC_AND_TAGS");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void addSlotWithTagsAndNoTopicPublishesSlotPresented() {

        // given - the live "New topic" branch: alice signs herself up for something not in the backlog
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(sessionWith().build()));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var session = sut.addSlot(7L, 21L, new AddSlotRequest("Kafka 101", null, 15, null, List.of("kafka")));

        // then - the gap that produced no expertise signal at all is closed
        assertThat(session.sections().get(0).slots().get(0).tags()).containsExactly("kafka");
        assertThat(publishedEvent()).isInstanceOfSatisfying(SlotPresented.class, sp -> {
            assertThat(sp.presenterUserIds()).containsExactly("alice");
            assertThat(sp.tags()).containsExactly("kafka");
        });
    }

    @Test
    void editingASlotsTagsRepublishes() {

        // given - a topicless slot alice presents, tagged "kafka"
        var entity = sessionWith(slot(11L, null, Set.of("kafka"), "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - she corrects the subject
        var session = sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Kafka 101", null, null, null, List.of("flink")));

        // then - the new subject replaces the old one, on the slot and in the event
        assertThat(session.sections().get(0).slots().get(0).tags()).containsExactly("flink");
        assertThat(publishedEvent()).isInstanceOfSatisfying(SlotPresented.class,
                sp -> assertThat(sp.tags()).containsExactly("flink"));
    }

    @Test
    void editingASlotWithoutTagsKeepsItsSubject() {

        // given - a backfilled, topicless slot alice presents, tagged "kafka"
        var entity = sessionWith(slot(11L, null, Set.of("kafka"), "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - someone fixes a typo in the title; the edit form sends no tags at all
        var session = sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Kafka 101", null, null, null, null));

        // then - omitted is not "clear": alice keeps her subject, and her expertise with it
        assertThat(session.sections().get(0).slots().get(0).tags()).containsExactly("kafka");
        assertThat(publishedEvent()).isInstanceOfSatisfying(SlotPresented.class,
                sp -> assertThat(sp.tags()).containsExactly("kafka"));
    }

    @Test
    void editingASlotWithAnEmptyTagListClearsItsSubject() {

        // given - the same slot
        var entity = sessionWith(slot(11L, null, Set.of("kafka"), "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - alice deliberately removes every tag she had put on it
        var session = sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Kafka 101", null, null, null, List.of()));

        // then - said explicitly, clearing the subject is honoured
        assertThat(session.sections().get(0).slots().get(0).tags()).isEmpty();
        assertThat(publishedEvent()).isInstanceOf(SlotCleared.class);
    }

    @Test
    void attachingATopicToASlotThatKeepsItsOwnTagsIsRefused() {

        // given - a topicless slot that already carries "kafka"
        var entity = sessionWith(slot(11L, null, Set.of("kafka"), "alice")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");

        // when - a topic is attached and no tags are sent, so the slot's own tags stay
        // then - the invariant is read off the effective subject, not off the request: two subjects, refused
        assertThatThrownBy(() -> sut.editSlot(7L, 21L, 11L, new EditSlotRequest("Kafka 101", 50L, null, null, null)))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SLOT_TOPIC_AND_TAGS");
        verify(sessionRepository, never()).save(any());
        verify(topicRepository, never()).save(any());
    }

    @Test
    void signingUpToATopiclessTaggedSlotPublishesSlotPresented() {

        // given - an organizer left an open slot for a talk that was never proposed, tagged "kafka"
        var entity = sessionWith(slot(11L, null, Set.of("kafka"))).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - alice takes it
        sut.signUp(7L, 21L, 11L);

        // then - signing up reads the subject off the slot, so alice is credited for kafka
        assertThat(publishedEvent()).isInstanceOfSatisfying(SlotPresented.class, sp -> {
            assertThat(sp.presenterUserIds()).containsExactly("alice");
            assertThat(sp.tags()).containsExactly("kafka");
        });
    }

    @Test
    void addPresenterOnATopiclessTaggedSlotPublishesSlotPresented() {

        // given - the same open, topicless, tagged slot
        var entity = sessionWith(slot(11L, null, Set.of("kafka"))).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("orga");
        when(communityAccess.resolveMember(1L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - an organizer assigns alice by name, as when a presenter is discovered after the fact
        sut.addPresenter(7L, 21L, 11L, "Alice Dupont");

        // then
        assertThat(publishedEvent()).isInstanceOfSatisfying(SlotPresented.class, sp -> {
            assertThat(sp.presenterUserIds()).containsExactly("alice-id");
            assertThat(sp.tags()).containsExactly("kafka");
        });
    }

    @Test
    void withdrawingFromATopiclessTaggedSlotLeavesTheSurvivingPresenterWithTheSubject() {

        // given - a topicless slot tagged "kafka" that alice and bob co-present
        var entity = sessionWith(slot(11L, null, Set.of("kafka"), "alice", "bob")).build();
        when(organizationContext.organizationId()).thenReturn("default");
        when(sessionRepository.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(currentUser.userId()).thenReturn("alice");
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - alice drops out
        sut.withdraw(7L, 21L, 11L);

        // then - the talk still happens, still about kafka, and bob still gets the credit for it
        assertThat(publishedEvent()).isInstanceOfSatisfying(SlotPresented.class, sp -> {
            assertThat(sp.presenterUserIds()).containsExactly("bob");
            assertThat(sp.tags()).containsExactly("kafka");
        });
    }

    @Test
    void tagsAreCleanedTheSameWayTopicTagsAre() {

        // given
        organizerOnAnEmptySession();
        when(communityAccess.resolveMember(1L, "Alice Dupont")).thenReturn(new MemberMatch.Matched("alice-id"));
        var messy = new ArrayList<>(Arrays.asList("  kafka  ", "KAFKA", "", "   ", null, "x".repeat(51)));
        for (var i = 1; i <= 12; i++) {
            messy.add("tag" + i);
        }

        // when
        sut.recordSlot(7L, 21L, new RecordSlotRequest("Kafka 101", 15, null, List.of("Alice Dupont"), messy));

        // then - stripped, blank-free, >50 chars dropped, case-insensitively deduped, capped at 10
        assertThat(publishedEvent()).isInstanceOfSatisfying(SlotPresented.class, sp -> assertThat(sp.tags())
                .containsExactly("kafka", "tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9"));
    }
}
