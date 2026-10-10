package dev.axonia.knowledge.application;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.context.ApplicationEventPublisher;

import dev.axonia.community.api.MemberMatch;
import dev.axonia.knowledge.api.ArtifactContributed;
import dev.axonia.knowledge.api.ArtifactMother;
import dev.axonia.knowledge.application.port.out.ArtifactStore;
import dev.axonia.knowledge.application.port.out.CommunityAccess;
import dev.axonia.knowledge.application.port.out.CurrentUser;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.knowledge.domain.StoredArtifact;
import dev.axonia.knowledge.domain.StoredArtifactMother;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Whose work an artifact is can be corrected, by whoever the community lets credit contributions, and only through this
 * command: an edit never moves credit. The republished {@link ArtifactContributed} is what moves the expertise signals.
 */
@MockitoSettings
class ArtifactContributorChangeTest {

    @Mock
    private ArtifactStore artifactStore;
    @Mock
    private ArtifactMapper artifactMapper;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ArtifactCreditService sut;

    @BeforeEach
    void seedSeams() {

        sut = new ArtifactCreditService(artifactStore, artifactMapper, new ContributorResolver(communityAccess),
                currentUser, organizationContext, eventPublisher);
        lenient().when(organizationContext.organizationId()).thenReturn("default");
        lenient().when(currentUser.userId()).thenReturn("olive");
        lenient().when(artifactStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(artifactMapper.toArtifact(any())).thenReturn(ArtifactMother.any().id(7L).build());
    }

    private void artifactIs(String status) {

        var entity = StoredArtifactMother.any().id(7L).communityId(1L).contributorUserId("olive").status(status)
                .tags(Set.of("kafka")).build();
        when(artifactStore.find("default", 7L)).thenReturn(Optional.of(entity));
    }

    @Test
    void movesTheCreditAndRepublishesItForTheNewContributor() {

        // given
        artifactIs("ACTIVE");
        when(communityAccess.canCreditContributions(1L, "olive")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "alice-id")).thenReturn(new MemberMatch.Matched("alice-id"));

        // when
        sut.changeContributor(7L, new ChangeContributorRequest("alice-id"));

        // then the row credits Alice
        var saved = ArgumentCaptor.forClass(StoredArtifact.class);
        verify(artifactStore).save(saved.capture());
        assertThat(saved.getValue().contributorUserId()).isEqualTo("alice-id");

        // then the graph is told about Alice, with the artifact's own tags
        var event = ArgumentCaptor.forClass(ArtifactContributed.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().contributorUserId()).isEqualTo("alice-id");
        assertThat(event.getValue().communityId()).isEqualTo(1L);
        assertThat(event.getValue().tags()).containsExactly("kafka");
    }

    @Test
    void refusesACallerTheCommunityDoesNotLetCredit() {

        // given
        artifactIs("ACTIVE");
        when(communityAccess.canCreditContributions(1L, "olive")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.changeContributor(7L, new ChangeContributorRequest("alice-id")))
                .isInstanceOf(ForbiddenException.class);
        verify(artifactStore, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void refusesSomebodyWhoIsNotAMember_withTheCodeCaptureUses() {

        // given
        artifactIs("ACTIVE");
        when(communityAccess.canCreditContributions(1L, "olive")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "stranger")).thenReturn(new MemberMatch.NotFound());

        // when / then
        assertThatThrownBy(() -> sut.changeContributor(7L, new ChangeContributorRequest("stranger")))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CONTRIBUTOR_NOT_FOUND");
        verify(artifactStore, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void refusesAnUnknownArtifact() {

        // given
        when(artifactStore.find("default", 7L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.changeContributor(7L, new ChangeContributorRequest("alice-id")))
                .isInstanceOf(NotFoundException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void namingTheCurrentContributorChangesNothing() {

        // given
        artifactIs("ACTIVE");
        when(communityAccess.canCreditContributions(1L, "olive")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "olive")).thenReturn(new MemberMatch.Matched("olive"));

        // when
        sut.changeContributor(7L, new ChangeContributorRequest("olive"));

        // then
        verify(artifactStore, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void correctsAnArchivedArtifactWithoutProjectingIt() {

        // given
        artifactIs("ARCHIVED");
        when(communityAccess.canCreditContributions(1L, "olive")).thenReturn(true);
        when(communityAccess.resolveMember(1L, "alice-id")).thenReturn(new MemberMatch.Matched("alice-id"));

        // when
        sut.changeContributor(7L, new ChangeContributorRequest("alice-id"));

        // then the correction is stored, and nothing reaches the graph until a restore republishes it
        var saved = ArgumentCaptor.forClass(StoredArtifact.class);
        verify(artifactStore).save(saved.capture());
        assertThat(saved.getValue().contributorUserId()).isEqualTo("alice-id");
        verify(eventPublisher, never()).publishEvent(any());
    }
}
