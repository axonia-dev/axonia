package dev.axonia.knowledge.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.ForbiddenException;
import dev.axonia.community.ContributionPolicy;
import dev.axonia.knowledge.CommunityAccess;
import dev.axonia.knowledge.SessionAccess;
import dev.axonia.knowledge.SessionRef;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@MockitoSettings
class ResourceAttachAuthorizerTest {

    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private SessionAccess sessionAccess;

    private ResourceAttachAuthorizer sut;

    @BeforeEach
    void setUp() {

        sut = new ResourceAttachAuthorizer(communityAccess, new SessionProvenanceGuard(sessionAccess));
    }

    @Test
    void heldSession_opensToAnyMember() {

        assertThatCode(() -> sut.requireScheduleManagerUnlessHeld(1L, "alice", new SessionRef(100L, 1L, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void notHeld_organizerPasses() {

        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(true);

        assertThatCode(() -> sut.requireScheduleManagerUnlessHeld(1L, "alice", new SessionRef(100L, 1L, false)))
                .doesNotThrowAnyException();
    }

    @Test
    void notHeld_scheduleManagementPolicyPasses() {

        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, true));

        assertThatCode(() -> sut.requireScheduleManagerUnlessHeld(1L, "alice", new SessionRef(100L, 1L, false)))
                .doesNotThrowAnyException();
    }

    @Test
    void notHeld_plainMemberRefused() {

        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, false));

        assertThatThrownBy(() -> sut.requireScheduleManagerUnlessHeld(1L, "alice", new SessionRef(100L, 1L, false)))
                .isInstanceOf(ForbiddenException.class);
    }

    /** No session reference at all fails closed to the strict arm, same as an unheld one. */
    @Test
    void nullSession_refusesAPlainMember() {

        when(communityAccess.isOrganizer(1L, "alice")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, false));

        assertThatThrownBy(() -> sut.requireScheduleManagerUnlessHeld(1L, "alice", null))
                .isInstanceOf(ForbiddenException.class);
    }
}
