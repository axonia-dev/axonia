package dev.axonia.knowledge;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

import dev.axonia.ActionableForbiddenException;
import dev.axonia.ConflictException;
import dev.axonia.community.MemberMatch;
import dev.axonia.knowledge.internal.ArtifactAuthorizer;
import dev.axonia.knowledge.internal.ArtifactEntity;
import dev.axonia.knowledge.internal.ArtifactMapper;
import dev.axonia.knowledge.internal.ArtifactRepository;
import dev.axonia.knowledge.internal.ArtifactTypeEntity;
import dev.axonia.knowledge.internal.ArtifactTypeRepository;
import dev.axonia.knowledge.internal.AttachmentEntity;
import dev.axonia.knowledge.internal.AttachmentRepository;
import dev.axonia.knowledge.internal.ContributorResolver;
import dev.axonia.knowledge.internal.ResourceAttachAuthorizer;
import dev.axonia.knowledge.internal.SessionProvenanceGuard;
import dev.axonia.knowledge.mother.ArtifactMother;

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
 * An artifact may name its contributor, so an organizer backfilling a library credits the people who did the work
 * instead of themselves. The published {@link ArtifactContributed} carries the resolved contributor: that field, and
 * nothing else, is what the expertise graph learns from.
 */
@MockitoSettings
class ArtifactAttributionTest {

    private static final String REFUSED = "Only an organizer, or a member the community lets credit contributions, "
            + "may credit a contribution to another member. Omit contributor to record this contribution as your own.";

    @Mock
    private ArtifactRepository artifactRepository;
    @Mock
    private ArtifactTypeRepository artifactTypeRepository;
    @Mock
    private ArtifactMapper artifactMapper;
    @Mock
    private ArtifactAuthorizer artifactAuthorizer;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SessionAccess sessionAccess;

    private ArtifactService sut;

    @BeforeEach
    void seedSeams() {

        // The resolver, guard and authorizer are real, over the mocked ports: the rules under test are the ones that
        // ship.
        var sessionProvenanceGuard = new SessionProvenanceGuard(sessionAccess);
        sut = new ArtifactService(artifactRepository, artifactTypeRepository, artifactMapper, artifactAuthorizer,
                communityAccess, new ContributorResolver(communityAccess), currentUser, organizationContext,
                attachmentRepository, eventPublisher, sessionProvenanceGuard,
                new ResourceAttachAuthorizer(communityAccess, sessionProvenanceGuard));
        when(organizationContext.organizationId()).thenReturn("default");
        when(currentUser.userId()).thenReturn("alice");
        when(communityAccess.communityIdForSlug("backend-guild")).thenReturn(1L);
        when(communityAccess.isMember(1L, "alice")).thenReturn(true);
        lenient().when(artifactTypeRepository.findByOrganizationIdAndId("default", 1L))
                .thenReturn(Optional.of(ArtifactTypeEntity.builder().id(1L).label("Recording").build()));
        lenient().when(artifactRepository.save(any()))
                .thenAnswer(inv -> ((ArtifactEntity) inv.getArgument(0)).toBuilder().id(10L).build());
        lenient().when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(10L).build());
    }

    private static CreateArtifactRequest request(String contributor) {

        return new CreateArtifactRequest("Event sourcing, two years on", 1L, "https://videos.dev/es", "A talk",
                Set.of("event-sourcing"), null, contributor);
    }

    private ArtifactContributed publishedEvent() {

        var captor = ArgumentCaptor.forClass(ArtifactContributed.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void capturesInTheCallersNameWhenNoContributorIsGiven() {

        // when
        sut.capture("backend-guild", request(null));

        // then
        assertThat(publishedEvent().contributorUserId()).isEqualTo("alice");
        verify(communityAccess, never()).resolveMember(anyLong(), anyString());
        verify(communityAccess, never()).canCreditContributions(anyLong(), anyString());
    }

    @Test
    void capturesInAnotherMembersNameWhenAnOrganizerNamesThem() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "Bob Martin")).thenReturn(new MemberMatch.Matched("bob-id"));

        // when
        sut.capture("backend-guild", request("Bob Martin"));

        // then the row itself is attributed to Bob, not to the organizer who typed it in
        var captor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(captor.capture());
        assertThat(captor.getValue().getContributorUserId()).isEqualTo("bob-id");
    }

    @Test
    void publishesArtifactContributedForTheNamedMemberNotTheCaller() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "Bob Martin")).thenReturn(new MemberMatch.Matched("bob-id"));

        // when
        sut.capture("backend-guild", request("Bob Martin"));

        // then the expertise graph learns about Bob: this is the whole point of the feature
        var event = publishedEvent();
        assertThat(event.contributorUserId()).isEqualTo("bob-id");
        assertThat(event.communityId()).isEqualTo(1L);
        assertThat(event.tags()).containsExactly("event-sourcing");
    }

    @Test
    void refusesWhenTheNamedContributorMatchesNobody_forACallerWhoMayCredit() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "J. Smith")).thenReturn(new MemberMatch.NotFound());

        // when / then
        assertThatThrownBy(() -> sut.capture("backend-guild", request("J. Smith")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("J. Smith").extracting("code")
                .isEqualTo("CONTRIBUTOR_NOT_FOUND");
        verify(artifactRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void refusesWhenTheNamedContributorMatchesSeveralMembers_forACallerWhoMayCredit() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "Bob")).thenReturn(new MemberMatch.Ambiguous(3));

        // when / then
        assertThatThrownBy(() -> sut.capture("backend-guild", request("Bob"))).isInstanceOf(ConflictException.class)
                .hasMessageContaining("3").hasMessageContaining("Bob").extracting("code")
                .isEqualTo("CONTRIBUTOR_AMBIGUOUS");
        verify(artifactRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    static Stream<MemberMatch> anyRosterOutcome() {

        return Stream.of(new MemberMatch.Matched("bob-id"), new MemberMatch.NotFound(), new MemberMatch.Ambiguous(3));
    }

    /**
     * The roster is never asked on behalf of a caller without the axis, so the answer cannot tell one match, no match
     * and several matches apart: otherwise a member could probe other members' email fragments.
     */
    @ParameterizedTest
    @MethodSource("anyRosterOutcome")
    void refusesACallerWithoutTheAxisIdenticallyWhateverTheRosterWouldMatch(MemberMatch outcome) {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(false);
        lenient().when(communityAccess.resolveMember(anyLong(), anyString())).thenReturn(outcome);

        // when / then
        assertThatThrownBy(() -> sut.capture("backend-guild", request("bob")))
                .isExactlyInstanceOf(ActionableForbiddenException.class).hasMessage(REFUSED);
        verify(communityAccess, never()).resolveMember(anyLong(), anyString());
        verify(artifactRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void refusesACallerWithoutTheAxisIdenticallyForAnInvalidType() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(false);
        var request = new CreateArtifactRequest("Event sourcing, two years on", 99L, "https://videos.dev/es", "A talk",
                Set.of("event-sourcing"), null, "bob");

        // when / then the refusal comes before the type check, so an invalid type is no side-effect-free probe
        assertThatThrownBy(() -> sut.capture("backend-guild", request))
                .isExactlyInstanceOf(ActionableForbiddenException.class).hasMessage(REFUSED);
        verify(communityAccess, never()).resolveMember(anyLong(), anyString());
    }

    @Test
    void refusesACallerWithoutTheAxisWhoNamesThemselvesByAFragment() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(false);
        when(communityAccess.identifiesExactly("alice", "Alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.capture("backend-guild", request("Alice")))
                .isExactlyInstanceOf(ActionableForbiddenException.class).hasMessage(REFUSED);
        verify(communityAccess, never()).resolveMember(anyLong(), anyString());
    }

    @Test
    void forbidsANonPrivilegedCallerFromCreditingSomeoneElse() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.capture("backend-guild", request("Bob Martin")))
                .isInstanceOf(ActionableForbiddenException.class).hasMessage(REFUSED);
        verify(artifactRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void letsAnyMemberNameThemselvesById_withoutTheAxis() {

        // when
        sut.capture("backend-guild", request(" alice "));

        // then naming yourself is not "crediting somebody else", so neither the axis nor the roster is asked
        assertThat(publishedEvent().contributorUserId()).isEqualTo("alice");
        verify(communityAccess, never()).canCreditContributions(anyLong(), anyString());
        verify(communityAccess, never()).resolveMember(anyLong(), anyString());
    }

    @Test
    void letsAnyMemberNameThemselvesByDisplayName_withoutTheAxis() {

        // given
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(false);
        when(communityAccess.identifiesExactly("alice", "Alice Dupont")).thenReturn(true);

        // when
        sut.capture("backend-guild", request("Alice Dupont"));

        // then
        assertThat(publishedEvent().contributorUserId()).isEqualTo("alice");
        verify(communityAccess, never()).resolveMember(anyLong(), anyString());
    }

    @Test
    void keepsTheCallerAsAttacherWhenAnOrganizerNamesTheContributor() {

        // given an organizer backfilling a recording that was auto-attached from a session segment
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(true);
        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "Bob Martin")).thenReturn(new MemberMatch.Matched("bob-id"));
        when(sessionAccess.session(5L)).thenReturn(new SessionRef(5L, 1L, false));
        when(sessionAccess.sessionIdOfSlot(7L)).thenReturn(Optional.of(5L));
        var provenance = new ProvenanceRequest(5L, 7L, "auto-attached");
        var request = new CreateArtifactRequest("Event sourcing, two years on", 1L, "https://videos.dev/es", "A talk",
                Set.of("event-sourcing"), provenance, "Bob Martin");

        // when
        sut.capture("backend-guild", request);

        // then the artifact credits Bob: he is the one who did the work
        var artifactCaptor = ArgumentCaptor.forClass(ArtifactEntity.class);
        verify(artifactRepository).save(artifactCaptor.capture());
        assertThat(artifactCaptor.getValue().getContributorUserId()).isEqualTo("bob-id");

        // then the attachment still records alice: ResourceService authorizes detaching off attachedByUserId,
        // so it must stay the person who actually performed the attach, not the contributor being credited
        var attachmentCaptor = ArgumentCaptor.forClass(AttachmentEntity.class);
        verify(attachmentRepository).save(attachmentCaptor.capture());
        assertThat(attachmentCaptor.getValue().getAttachedByUserId()).isEqualTo("alice");
    }

    @Test
    void treatsABlankContributorAsAbsent() {

        // when
        sut.capture("backend-guild", request("   "));

        // then
        assertThat(publishedEvent().contributorUserId()).isEqualTo("alice");
        verify(communityAccess, never()).resolveMember(anyLong(), anyString());
        verify(communityAccess, never()).canCreditContributions(anyLong(), anyString());
    }

    @Test
    void letsAMemberTheCommunityLetsCreditNameSomeoneElse() {

        // given a plain member, in a community that opened Credit contributions to members
        when(communityAccess.canCreditContributions(1L, "alice")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "Bob Martin")).thenReturn(new MemberMatch.Matched("bob-id"));

        // when
        sut.capture("backend-guild", request("Bob Martin"));

        // then the axis, not the organizer role, decided it
        assertThat(publishedEvent().contributorUserId()).isEqualTo("bob-id");
        verify(communityAccess, never()).isOrganizer(anyLong(), anyString());
    }
}
