package dev.axonia.events.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import dev.axonia.community.api.TopicManagementPolicy;
import dev.axonia.events.api.ProposeTopicRequest;
import dev.axonia.events.api.Topic;
import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicQuery;
import dev.axonia.events.api.TopicRemoved;
import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.api.TopicTagsChanged;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.application.port.out.TopicVoteStore;
import dev.axonia.events.application.port.out.UserProfileAccess;
import dev.axonia.events.domain.TopicCounts;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicRecordMother;
import dev.axonia.events.domain.TopicSchedule;
import dev.axonia.events.domain.TopicTag;
import dev.axonia.events.domain.TopicVoteRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class TopicServiceTest {

    @Mock
    private TopicStore topicStore;

    @Mock
    private TopicVoteStore topicVoteStore;

    @Mock
    private TopicAuthorizer topicAuthorizer;

    @Mock
    private TopicMapper topicMapper;

    @Mock
    private SlotService slotService;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private UserProfileAccess userProfileAccess;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private TopicService sut;

    @BeforeEach
    void buildSut() {

        var topicReadModel = new TopicReadModel(topicVoteStore, userProfileAccess, topicAuthorizer, topicMapper,
                currentUser, topicStore);
        var topicMemberResolver = new TopicMemberResolver(communityAccess);
        sut = new TopicService(topicStore, topicVoteStore, topicAuthorizer, slotService, communityAccess, currentUser,
                organizationContext, eventPublisher, topicReadModel, topicMemberResolver);
    }

    @BeforeEach
    void seedSeams() {

        when(organizationContext.organizationId()).thenReturn("default");
        lenient().when(currentUser.userId()).thenReturn("alice");
        lenient().when(communityAccess.communityIdForSlug("backend-guild")).thenReturn(1L);
        lenient().when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        lenient().when(topicVoteStore.countByOrganizationIdAndTopicId("default", 1L)).thenReturn(0L);
    }

    @BeforeEach
    void stubDefaults() {

        lenient().when(userProfileAccess.displayName(any(), any())).thenReturn("Test User");
        lenient().when(topicVoteStore.existsByOrganizationIdAndTopicIdAndUserId(any(), any(), any())).thenReturn(false);
        lenient().when(topicVoteStore.findByOrganizationIdAndTopicIdIn(any(), any())).thenReturn(List.of());
        lenient().when(communityAccess.topicManagementPolicy(anyLong()))
                .thenReturn(TopicManagementPolicy.ORGANIZER_ONLY);
        lenient().when(communityAccess.isOrganizer(anyLong(), any())).thenReturn(false);
        lenient().when(communityAccess.isMember(anyLong(), any())).thenReturn(true);
    }

    @Test
    void propose_persistsProposedTopicForMember() {

        // given
        when(topicStore.save(any())).thenAnswer(inv -> ((TopicRecord) inv.getArgument(0)).toBuilder().id(1L).build());

        // when
        var actual = sut.propose("backend-guild",
                new ProposeTopicRequest(TopicKind.REQUEST, "ES intro", "please", null, null));

        // then
        assertThat(actual.status()).isEqualTo(TopicStatus.PROPOSED);
        assertThat(actual.proposerUserId()).isEqualTo("alice");
        assertThat(actual.proposerDisplayName()).isEqualTo("Test User");
    }

    @Test
    void propose_rejectsNonMember() {

        // given
        when(communityAccess.isMember(1L, "alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(
                () -> sut.propose("backend-guild", new ProposeTopicRequest(TopicKind.OFFER, "x", "y", null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(topicStore, never()).save(any());
    }

    @Test
    void vote_duplicateMapsToAlreadyVoted() {

        // given
        when(topicStore.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(TopicRecordMother.any().id(1L).build()));
        when(topicVoteStore.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        // when / then
        assertThatThrownBy(() -> sut.vote(1L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("ALREADY_VOTED");
    }

    @Test
    void vote_incrementsVoteCount() {

        // given
        when(topicStore.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(TopicRecordMother.any().id(1L).build()));
        when(topicVoteStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(topicVoteStore.countByOrganizationIdAndTopicId("default", 1L)).thenReturn(1L);

        // when
        var actual = sut.vote(1L);

        // then
        assertThat(actual.voteCount()).isEqualTo(1L);
    }

    @Test
    void unvote_removesExistingVote() {

        // given
        var vote = TopicVoteRecord.builder().id(5L).organizationId("default").topicId(1L).userId("alice").build();
        when(topicVoteStore.findByOrganizationIdAndTopicIdAndUserId("default", 1L, "alice"))
                .thenReturn(Optional.of(vote));

        // when
        sut.unvote(1L);

        // then
        verify(topicVoteStore).delete(vote);
    }

    // Additional tests for TopicAuthorizer branch coverage (all 3 paths)

    @Test
    void authorizer_delegatesPermissionCheck() {

        // given - alice updates a topic; authorizer is a mock (no-op by default)
        var entity = TopicRecordMother.any().id(1L).proposerUserId("alice").build();
        when(topicStore.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(topicStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(topicVoteStore.countByOrganizationIdAndTopicId("default", 1L)).thenReturn(0L);

        // when - no exception expected since authorizer mock is a no-op by default
        sut.update(1L, new UpdateTopicRequest(TopicKind.REQUEST, "New title", "desc", null));

        // then - verify authorizer was called with the entity's communityId, not the entity itself
        verify(topicAuthorizer).requireTopicManagementPermission(entity.getCommunityId(), "alice");
    }

    @Test
    void get_returnsTopic() {

        // given
        when(topicStore.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(TopicRecordMother.any().id(1L).build()));

        // when
        var actual = sut.get(1L);

        // then
        assertThat(actual.id()).isEqualTo(1L);
        assertThat(actual.voteCount()).isEqualTo(0L);
    }

    @Test
    void get_notFoundThrows() {

        // given
        when(topicStore.findByOrganizationIdAndId("default", 99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.get(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void get_includesHasVotedAndDisplayName() {

        // given
        var entity = TopicRecordMother.any().id(1L).build();
        when(topicStore.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(topicVoteStore.countByOrganizationIdAndTopicId("default", 1L)).thenReturn(2L);
        when(topicVoteStore.existsByOrganizationIdAndTopicIdAndUserId("default", 1L, "alice")).thenReturn(true);
        when(userProfileAccess.displayName("default", entity.getProposerUserId())).thenReturn("Alice");

        // when
        var topic = sut.get(1L);

        // then
        assertThat(topic.hasVoted()).isTrue();
        assertThat(topic.proposerDisplayName()).isEqualTo("Alice");
        assertThat(topic.voteCount()).isEqualTo(2L);
    }

    @Test
    void list_filtersByKindAndStatus() {

        // given
        var entity = TopicRecordMother.any().id(1L).build();
        var filter = new TopicFilter(TopicKind.OFFER, TopicStatus.PROPOSED);
        when(topicStore.findFiltered(eq("default"), eq(1L), eq(filter), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        // when
        var result = sut.list("backend-guild",
                TopicQuery.builder().kind(TopicKind.OFFER).status(TopicStatus.PROPOSED).build(), PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void list_filtersByKind() {

        // given
        var entity = TopicRecordMother.any().id(1L).build();
        var filter = new TopicFilter(TopicKind.OFFER, null);
        when(topicStore.findFiltered(eq("default"), eq(1L), eq(filter), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        // when
        var result = sut.list("backend-guild", TopicQuery.builder().kind(TopicKind.OFFER).build(),
                PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void list_filtersByStatus() {

        // given
        var entity = TopicRecordMother.any().id(1L).build();
        var filter = new TopicFilter(null, TopicStatus.PROPOSED);
        when(topicStore.findFiltered(eq("default"), eq(1L), eq(filter), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        // when
        var result = sut.list("backend-guild", TopicQuery.builder().status(TopicStatus.PROPOSED).build(),
                PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void list_noFilters() {

        // given
        var entity = TopicRecordMother.any().id(1L).build();
        var filter = new TopicFilter(null, null);
        when(topicStore.findFiltered(eq("default"), eq(1L), eq(filter), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        // when
        var result = sut.list("backend-guild", TopicQuery.builder().build(), PageRequest.of(0, 20));

        // then
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void list_enrichesScheduledTopicsWithScheduleInfo() {
        var scheduled = TopicRecordMother.any().status(TopicStatus.SCHEDULED).build();
        // entity id is null from the Mother; give it one for the lookup
        var withId = scheduled.toBuilder().id(5L).build();
        when(topicStore.findFiltered(eq("default"), eq(1L), eq(new TopicFilter(null, null)), any()))
                .thenReturn(new PageImpl<>(java.util.List.of(withId)));
        when(topicStore.findSchedules("default", java.util.List.of(5L))).thenReturn(java.util.List.of(
                new TopicSchedule(5L, 12L, 7L, 9L, java.time.Instant.parse("2099-06-14T18:00:00Z"), "Frontend Dojo")));
        lenient().when(topicVoteStore.countByOrganizationIdAndTopicId("default", 5L)).thenReturn(0L);

        var page = sut.list("backend-guild", TopicQuery.builder().build(), PageRequest.of(0, 20));

        assertThat(page.getContent().get(0).scheduledFor().scheduleLabel()).isEqualTo("Frontend Dojo");
        assertThat(page.getContent().get(0).scheduledFor().sessionId()).isEqualTo(12L);
    }

    @Test
    void list_derivesVoteCountsAndVotedSetFromSingleBatchAndResolvesManageRightsOncePerCommunity() {

        // given - two topics in the same community
        var t1 = TopicRecordMother.any().id(10L).build();
        var t2 = TopicRecordMother.any().id(11L).build();
        when(topicStore.findFiltered(eq("default"), eq(1L), eq(new TopicFilter(null, null)), any()))
                .thenReturn(new PageImpl<>(List.of(t1, t2)));
        // a single batch read returns every vote on the page: topic 10 has alice + bob, topic 11 has bob
        when(topicVoteStore.findByOrganizationIdAndTopicIdIn(eq("default"), any()))
                .thenReturn(List.of(TopicVoteRecord.builder().topicId(10L).userId("alice").build(),
                        TopicVoteRecord.builder().topicId(10L).userId("bob").build(),
                        TopicVoteRecord.builder().topicId(11L).userId("bob").build()));

        // when
        var page = sut.list("backend-guild", TopicQuery.builder().build(), PageRequest.of(0, 20));

        // then - counts and the current user's voted flag derive from the one batch read
        assertThat(page.getContent()).extracting(Topic::id, Topic::voteCount, Topic::hasVoted)
                .containsExactly(tuple(10L, 2L, true), tuple(11L, 1L, false));
        // and manage-rights are resolved once for the shared community, not once per row (no N+1)
        verify(topicAuthorizer, times(1)).canManageInCommunity(1L, "alice");
        verify(topicVoteStore, never()).countByOrganizationIdAndTopicId(eq("default"), anyLong());
    }

    @Test
    void returnToBacklog_removesTheSlotCarryingTheTopic() {
        when(topicStore.findSchedules("default", List.of(5L))).thenReturn(List.of(
                new TopicSchedule(5L, 12L, 7L, 9L, java.time.Instant.parse("2099-06-14T18:00:00Z"), "Frontend Dojo")));

        sut.returnToBacklog(5L);

        verify(slotService).removeSlot(12L, 7L, 9L);
    }

    @Test
    void returnToBacklog_throwsWhenTopicIsNotScheduled() {
        when(topicStore.findSchedules("default", List.of(5L))).thenReturn(List.of());

        assertThatThrownBy(() -> sut.returnToBacklog(5L)).isInstanceOf(ConflictException.class);
        verify(slotService, never()).removeSlot(any(), any(), any());
    }

    @Test
    void update_changesTopicForProposer() {

        // given
        when(topicStore.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(TopicRecordMother.any().id(1L).build()));
        when(topicStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        var actual = sut.update(1L, new UpdateTopicRequest(TopicKind.OFFER, "Renamed", "desc", null));

        // then
        assertThat(actual.title()).isEqualTo("Renamed");
        assertThat(actual.kind()).isEqualTo(TopicKind.OFFER);
    }

    @Test
    void update_delegatesForbiddenFromAuthorizer() {

        // given
        var entity = TopicRecordMother.any().id(1L).build();
        when(topicStore.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        doThrow(new ForbiddenException("nope")).when(topicAuthorizer)
                .requireTopicManagementPermission(entity.getCommunityId(), "alice");

        // when / then
        assertThatThrownBy(() -> sut.update(1L, new UpdateTopicRequest(TopicKind.OFFER, "X", "y", null)))
                .isInstanceOf(ForbiddenException.class);
        verify(topicStore, never()).save(any());
    }

    @Test
    void remove_deletesForProposer() {

        // given
        var entity = TopicRecordMother.any().id(1L).build();
        when(topicStore.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));

        // when
        sut.remove(1L);

        // then
        verify(topicStore).delete(entity);
    }

    @Test
    void remove_throwsWhenTopicIsNotProposed() {

        // given - a SCHEDULED topic cannot be deleted
        var entity = TopicRecordMother.any().id(1L).status(TopicStatus.SCHEDULED).build();
        when(topicStore.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));

        // when / then
        assertThatThrownBy(() -> sut.remove(1L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("NOT_PROPOSED");
        verify(topicStore, never()).delete(any());
    }

    @Test
    void get_canManage_anyMemberPolicyAllowsMember() {

        // given - alice can manage the community → canEdit = true
        var entity = TopicRecordMother.any().id(1L).build();
        when(topicStore.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(topicAuthorizer.canManageInCommunity(1L, "alice")).thenReturn(true);

        // when
        var topic = sut.get(1L);

        // then
        assertThat(topic.canEdit()).isTrue();
        assertThat(topic.canDelete()).isTrue();
    }

    @Test
    void unvote_notFoundThrows() {

        // given
        when(topicVoteStore.findByOrganizationIdAndTopicIdAndUserId("default", 1L, "alice"))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.unvote(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void vote_rejectsNonMember() {

        // given
        when(topicStore.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(TopicRecordMother.any().id(1L).build()));
        when(communityAccess.isMember(1L, "alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.vote(1L)).isInstanceOf(ForbiddenException.class);
        verify(topicVoteStore, never()).save(any());
    }

    @Test
    void propose_nullDescriptionStoredAsEmpty() {

        // given
        ArgumentCaptor<TopicRecord> captor = ArgumentCaptor.forClass(TopicRecord.class);
        when(topicStore.save(any())).thenAnswer(inv -> ((TopicRecord) inv.getArgument(0)).toBuilder().id(1L).build());

        // when
        var actual = sut.propose("backend-guild", new ProposeTopicRequest(TopicKind.REQUEST, "T", null, null, null));

        // then - verify the entity saved has empty description (null branch in description())
        verify(topicStore).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("");
        assertThat(actual.description()).isEqualTo("");
    }

    @Test
    void listPast_delegatesToFindPastAndEnrichesDoneTopics() {
        // given a DONE topic returned by findPast, with a resolvable past session
        var done = TopicRecordMother.any().status(TopicStatus.DONE).build().toBuilder().id(7L).build();
        when(topicStore.findPast(eq("default"), eq(1L), eq("kafka"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(done)));
        when(topicStore.findSchedules("default", List.of(7L))).thenReturn(List.of(
                new TopicSchedule(7L, 3L, 4L, 5L, java.time.Instant.parse("2001-01-01T18:00:00Z"), "Frontend Dojo")));
        lenient().when(topicVoteStore.countByOrganizationIdAndTopicId("default", 7L)).thenReturn(0L);

        // when
        var page = sut.listPast("backend-guild", "kafka", PageRequest.of(0, 20));

        // then - DONE topic carries its past session metadata
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).scheduledFor().scheduleLabel()).isEqualTo("Frontend Dojo");
    }

    // ---- tag cleaning ----

    @Test
    void create_collapsesEverySpellingOfATagOntoItsOneName() {

        // given - four ways of typing one subject, one of them not a subject at all
        var rawTags = List.of("Apache Kafka", " apache-kafka ", "APACHE KAFKA", "");
        var request = new ProposeTopicRequest(TopicKind.REQUEST, "ES intro", "please", rawTags, null);
        ArgumentCaptor<TopicRecord> captor = ArgumentCaptor.forClass(TopicRecord.class);
        when(topicStore.save(any())).thenAnswer(inv -> ((TopicRecord) inv.getArgument(0)).toBuilder().id(1L).build());

        // when
        sut.propose("backend-guild", request);

        // then - the one name the library and the expertise projection also know it by, so a curator can reach it
        verify(topicStore).save(captor.capture());
        assertThat(captor.getValue().getTags()).extracting(TopicTag::getTag).containsExactly("apache-kafka");
    }

    @Test
    void create_tagsExceedingFiftyCharsDropped() {

        // given
        var longTag = "a".repeat(51);
        var request = new ProposeTopicRequest(TopicKind.REQUEST, "ES intro", "please", List.of("ok", longTag), null);
        ArgumentCaptor<TopicRecord> captor = ArgumentCaptor.forClass(TopicRecord.class);
        when(topicStore.save(any())).thenAnswer(inv -> ((TopicRecord) inv.getArgument(0)).toBuilder().id(1L).build());

        // when
        sut.propose("backend-guild", request);

        // then - 51-char tag dropped, "ok" survives
        verify(topicStore).save(captor.capture());
        assertThat(captor.getValue().getTags()).extracting(TopicTag::getTag).containsExactly("ok");
    }

    @Test
    void create_capsAtTenTags() {

        // given - 12 ordered tags, only the first 10 the caller listed should be stored
        var rawTags = List.of("tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9", "tag10", "tag11",
                "tag12");
        var request = new ProposeTopicRequest(TopicKind.REQUEST, "ES intro", "please", rawTags, null);
        ArgumentCaptor<TopicRecord> captor = ArgumentCaptor.forClass(TopicRecord.class);
        when(topicStore.save(any())).thenAnswer(inv -> ((TopicRecord) inv.getArgument(0)).toBuilder().id(1L).build());

        // when
        sut.propose("backend-guild", request);

        // then - the caller's order decides which ten survive (tag11/tag12 are dropped), and the survivors are stored
        // in the canonical alphabetical order, which is why tag10 sorts between tag1 and tag2
        verify(topicStore).save(captor.capture());
        assertThat(captor.getValue().getTags()).extracting(TopicTag::getTag).containsExactly("tag1", "tag10", "tag2",
                "tag3", "tag4", "tag5", "tag6", "tag7", "tag8", "tag9");
    }

    @Test
    void update_persistsCleanedTags() {

        // given
        var entity = TopicRecordMother.any().id(1L).build();
        when(topicStore.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        ArgumentCaptor<TopicRecord> captor = ArgumentCaptor.forClass(TopicRecord.class);
        when(topicStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var request = new UpdateTopicRequest(TopicKind.OFFER, "New title", "desc", List.of(" Spring Boot ", "spring"));

        // when
        sut.update(1L, request);

        // then - re-tagging goes through the same one rule the first tagging did
        verify(topicStore).save(captor.capture());
        assertThat(captor.getValue().getTags()).extracting(TopicTag::getTag).containsExactly("spring", "spring-boot");
    }

    @Test
    void propose_tagsAppearsInReturnedTopic() {

        // given
        when(topicStore.save(any())).thenAnswer(inv -> {
            var e = (TopicRecord) inv.getArgument(0);
            return e.toBuilder().id(1L).build();
        });
        when(topicMapper.tags(any())).thenReturn(List.of("Kafka"));
        var request = new ProposeTopicRequest(TopicKind.REQUEST, "ES intro", "please", List.of("Kafka"), null);

        // when
        var result = sut.propose("backend-guild", request);

        // then
        assertThat(result.tags()).containsExactly("Kafka");
    }

    // ---- event publishing ----

    @Test
    void create_publishesTopicTagsChanged() {

        // given
        when(topicStore.save(any())).thenAnswer(inv -> ((TopicRecord) inv.getArgument(0)).toBuilder().id(42L).build());
        var request = new ProposeTopicRequest(TopicKind.OFFER, "DDD intro", "desc", List.of("ddd", " DDD "), null);

        // when
        sut.propose("backend-guild", request);

        // then - publisher called with TopicTagsChanged carrying id, communityId, proposer, kind, and cleaned tags
        ArgumentCaptor<TopicTagsChanged> captor = ArgumentCaptor.forClass(TopicTagsChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.topicId()).isEqualTo(42L);
        assertThat(event.communityId()).isEqualTo(1L);
        assertThat(event.proposerUserId()).isEqualTo("alice");
        assertThat(event.kind()).isEqualTo(TopicKind.OFFER);
        assertThat(event.tags()).containsExactly("ddd");
    }

    @Test
    void edit_publishesTopicTagsChanged() {

        // given
        var entity = TopicRecordMother.any().id(7L).proposerUserId("bob").kind(TopicKind.REQUEST).build();
        when(topicStore.findByOrganizationIdAndId("default", 7L)).thenReturn(Optional.of(entity));
        when(topicStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var request = new UpdateTopicRequest(TopicKind.REQUEST, "Refined title", "desc", List.of("spring", "boot"));

        // when
        sut.update(7L, request);

        // then - publisher called with updated tags and kind
        ArgumentCaptor<TopicTagsChanged> captor = ArgumentCaptor.forClass(TopicTagsChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.topicId()).isEqualTo(7L);
        assertThat(event.communityId()).isEqualTo(1L);
        assertThat(event.proposerUserId()).isEqualTo("bob");
        assertThat(event.kind()).isEqualTo(TopicKind.REQUEST);
        assertThat(event.tags()).containsExactlyInAnyOrder("spring", "boot");
    }

    @Test
    void delete_publishesTopicRemoved() {

        // given
        var entity = TopicRecordMother.any().id(3L).communityId(1L).build();
        when(topicStore.findByOrganizationIdAndId("default", 3L)).thenReturn(Optional.of(entity));

        // when
        sut.remove(3L);

        // then - publisher called with TopicRemoved carrying id and communityId
        ArgumentCaptor<TopicRemoved> captor = ArgumentCaptor.forClass(TopicRemoved.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.topicId()).isEqualTo(3L);
        assertThat(event.communityId()).isEqualTo(1L);
    }

    @Test
    void get_canManageButNotProposed_cannotDelete() {

        // given - alice can manage the community (canManage=true), but the topic is SCHEDULED, not PROPOSED
        // covers the `canManage && status == PROPOSED` → false branch (canManage true, status not PROPOSED)
        var entity = TopicRecordMother.any().id(1L).status(TopicStatus.SCHEDULED).build();
        when(topicStore.findByOrganizationIdAndId("default", 1L)).thenReturn(Optional.of(entity));
        when(topicAuthorizer.canManageInCommunity(1L, "alice")).thenReturn(true);

        // when
        var topic = sut.get(1L);

        // then - manage rights granted, but a non-proposed topic still cannot be deleted
        assertThat(topic.canEdit()).isTrue();
        assertThat(topic.canDelete()).isFalse();
    }

    @Test
    void propose_skipsNullTagElement() {

        // given - a tags list containing a null element exercises the `tag == null` skip branch in cleanTags
        var rawTags = java.util.Arrays.asList("kafka", null, "spring");
        ArgumentCaptor<TopicRecord> captor = ArgumentCaptor.forClass(TopicRecord.class);
        when(topicStore.save(any())).thenAnswer(inv -> ((TopicRecord) inv.getArgument(0)).toBuilder().id(1L).build());

        // when
        sut.propose("backend-guild", new ProposeTopicRequest(TopicKind.REQUEST, "T", "d", rawTags, null));

        // then - null element dropped, the two real tags survive
        verify(topicStore).save(captor.capture());
        assertThat(captor.getValue().getTags()).extracting(TopicTag::getTag).containsExactly("kafka", "spring");
    }

    // ---- counts (backlog tab bar) ----

    @Test
    void counts_delegatesToRepositoryForResolvedCommunity() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("backend-guild")).thenReturn(1L);
        when(topicStore.countByLifecycle("default", 1L))
                .thenReturn(TopicCounts.builder().proposed(3).scheduled(1).done(7).build());

        // when
        var actual = sut.counts("backend-guild");

        // then
        assertThat(actual).isEqualTo(TopicCounts.builder().proposed(3).scheduled(1).done(7).build());
    }
}
