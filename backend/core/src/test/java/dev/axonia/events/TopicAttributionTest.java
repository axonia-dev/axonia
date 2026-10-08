package dev.axonia.events;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.community.MemberMatch;
import dev.axonia.community.TopicManagementPolicy;
import dev.axonia.events.internal.TopicAuthorizer;
import dev.axonia.events.internal.TopicEntity;
import dev.axonia.events.internal.TopicMapper;
import dev.axonia.events.internal.TopicMemberResolver;
import dev.axonia.events.internal.TopicReadModel;
import dev.axonia.events.internal.TopicRepository;
import dev.axonia.events.internal.TopicVoteRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A topic may name its proposer, so an organizer backfilling a backlog credits the people who offered the talks instead
 * of themselves. The published {@link TopicTagsChanged} carries the resolved proposer: that field, and nothing else, is
 * what the expertise graph learns an OFFER from.
 */
@MockitoSettings
class TopicAttributionTest {

    @Mock
    private TopicRepository topicRepository;
    @Mock
    private TopicVoteRepository topicVoteRepository;
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

        var topicReadModel = new TopicReadModel(topicVoteRepository, userProfileAccess, topicAuthorizer, topicMapper,
                currentUser, topicRepository);
        var topicMemberResolver = new TopicMemberResolver(communityAccess);
        sut = new TopicService(topicRepository, topicVoteRepository, topicAuthorizer, slotService, communityAccess,
                currentUser, organizationContext, eventPublisher, topicReadModel, topicMemberResolver);
    }

    @BeforeEach
    void seedSeams() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("backend-guild")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        lenient().when(communityAccess.topicManagementPolicy(1L)).thenReturn(TopicManagementPolicy.ORGANIZER_ONLY);
        lenient().when(topicRepository.save(any()))
                .thenAnswer(inv -> ((TopicEntity) inv.getArgument(0)).toBuilder().id(10L).build());
    }

    private static ProposeTopicRequest request(String proposer) {

        return new ProposeTopicRequest(TopicKind.OFFER, "Event sourcing, two years on", "A talk I can give",
                List.of("event-sourcing"), proposer);
    }

    private TopicTagsChanged publishedEvent() {

        var captor = ArgumentCaptor.forClass(TopicTagsChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void proposesInTheCallersNameWhenNoProposerIsGiven() {

        // when
        sut.propose("backend-guild", request(null));

        // then
        assertThat(publishedEvent().proposerUserId()).isEqualTo("alice");
        verify(communityAccess, never()).resolveMember(anyLong(), anyString());
    }

    @Test
    void proposesInAnotherMembersNameWhenAnOrganizerNamesThem() {

        // given
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "Bob Martin")).thenReturn(new MemberMatch.Matched("bob-id"));

        // when
        sut.propose("backend-guild", request("Bob Martin"));

        // then the row itself is attributed to Bob, not to the organizer who typed it in
        var captor = ArgumentCaptor.forClass(TopicEntity.class);
        verify(topicRepository).save(captor.capture());
        assertThat(captor.getValue().getProposerUserId()).isEqualTo("bob-id");
    }

    @Test
    void publishesTopicTagsChangedForTheNamedMemberNotTheCaller() {

        // given
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "Bob Martin")).thenReturn(new MemberMatch.Matched("bob-id"));

        // when
        sut.propose("backend-guild", request("Bob Martin"));

        // then the expertise graph learns Bob OFFERED this: this is the whole point of the feature
        var event = publishedEvent();
        assertThat(event.proposerUserId()).isEqualTo("bob-id");
        assertThat(event.communityId()).isEqualTo(1L);
        assertThat(event.kind()).isEqualTo(TopicKind.OFFER);
        assertThat(event.tags()).containsExactly("event-sourcing");
    }

    @Test
    void refusesWhenTheNamedProposerMatchesNobody() {

        // given
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "J. Smith")).thenReturn(new MemberMatch.NotFound());

        // when / then
        assertThatThrownBy(() -> sut.propose("backend-guild", request("J. Smith")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("J. Smith").extracting("code")
                .isEqualTo("PROPOSER_NOT_FOUND");
        verify(topicRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void refusesWhenTheNamedProposerMatchesSeveralMembers() {

        // given
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "Bob")).thenReturn(new MemberMatch.Ambiguous(3));

        // when / then
        assertThatThrownBy(() -> sut.propose("backend-guild", request("Bob"))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("3").hasMessageContaining("Bob").extracting("code")
                .isEqualTo("PROPOSER_AMBIGUOUS");
        verify(topicRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void forbidsAPlainMemberFromNamingSomeoneElse() {

        // given
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.propose("backend-guild", request("Bob Martin")))
                .isInstanceOf(ForbiddenException.class);
        verify(communityAccess, never()).resolveMember(anyLong(), anyString());
        verify(topicRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void treatsABlankProposerAsAbsent() {

        // when
        sut.propose("backend-guild", request("   "));

        // then
        assertThat(publishedEvent().proposerUserId()).isEqualTo("alice");
        verify(communityAccess, never()).resolveMember(anyLong(), anyString());
    }
}
