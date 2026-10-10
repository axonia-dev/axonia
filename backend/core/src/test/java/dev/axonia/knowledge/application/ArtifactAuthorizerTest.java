package dev.axonia.knowledge.application;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.knowledge.application.port.out.CommunityAccess;
import dev.axonia.shared.api.error.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class ArtifactAuthorizerTest {

    @Mock
    private CommunityAccess communityAccess;

    @InjectMocks
    private ArtifactAuthorizer sut;

    @Test
    void contributorMayActOnOwnArtifact() {

        // then
        assertThatCode(() -> sut.requireContributorOrOrganizer(1L, "alice", "alice")).doesNotThrowAnyException();
    }

    @Test
    void organizerMayActOnAnyArtifact() {

        // given
        when(communityAccess.isOrganizer(1L, "bob")).thenReturn(true);

        // then
        assertThatCode(() -> sut.requireContributorOrOrganizer(1L, "alice", "bob")).doesNotThrowAnyException();
    }

    @Test
    void nonContributorNonOrganizerIsForbidden() {

        // given
        when(communityAccess.isOrganizer(1L, "bob")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.requireContributorOrOrganizer(1L, "alice", "bob"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void actor_readsTheOrganizerRoleOnceAcrossOwners() {

        // given
        when(communityAccess.isOrganizer(1L, "carol")).thenReturn(true);
        var actor = sut.actor(1L, "carol");

        // when
        var mayActForAlice = actor.isOwnerOrOrganizer("alice");
        var mayActForBob = actor.isOwnerOrOrganizer("bob");

        // then
        assertThat(mayActForAlice).isTrue();
        assertThat(mayActForBob).isTrue();
        verify(communityAccess, times(1)).isOrganizer(1L, "carol");
    }
}
