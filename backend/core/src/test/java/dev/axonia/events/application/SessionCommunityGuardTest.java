package dev.axonia.events.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The session community guard in isolation. Its callers reach it through a service, where a stubbed repository and a
 * resolved community make it easy to believe the guard ran when only the caller's own arrangement refused the write.
 */
@ExtendWith(MockitoExtension.class)
class SessionCommunityGuardTest {

    private static final String ORG = "default";
    private static final long COMMUNITY = 1L;
    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Mock
    private SessionStore sessionStore;

    private SessionCommunityGuard sut;

    @BeforeEach
    void setUp() {

        sut = new SessionCommunityGuard(sessionStore, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void sessionIs(long id, long communityId, String status, Instant scheduledAt) {

        when(sessionStore.findByOrganizationIdAndId(ORG, id)).thenReturn(Optional.of(SessionRecordMother.any().id(id)
                .communityId(communityId).status(status).scheduledAt(scheduledAt).build()));
    }

    @Test
    void aNullSessionIsNoClaimAtAllAndNeverReachesTheRepository() {

        assertThatCode(() -> sut.requireSessionInCommunity(ORG, null, COMMUNITY)).doesNotThrowAnyException();
        assertThatCode(() -> sut.requireUpcomingSessionInCommunity(ORG, null, COMMUNITY)).doesNotThrowAnyException();
    }

    @Test
    void aSessionOfThisCommunityPasses() {

        sessionIs(5L, COMMUNITY, "SCHEDULED", NOW.plusSeconds(3600));

        assertThatCode(() -> sut.requireSessionInCommunity(ORG, 5L, COMMUNITY)).doesNotThrowAnyException();
    }

    @Test
    void aSessionOfAnotherCommunityIsNotFound() {

        sessionIs(5L, 999L, "SCHEDULED", NOW.plusSeconds(3600));

        assertThatThrownBy(() -> sut.requireSessionInCommunity(ORG, 5L, COMMUNITY))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aSessionOfAnotherOrganizationIsNotFound() {

        when(sessionStore.findByOrganizationIdAndId(ORG, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.requireSessionInCommunity(ORG, 5L, COMMUNITY))
                .isInstanceOf(NotFoundException.class);
    }

    // A held session is history, so it may have raised the item even though nothing may be pinned to it.
    @Test
    void aHeldSessionStillPassesTheLooserOriginGuard() {

        sessionIs(5L, COMMUNITY, "HELD", NOW.minusSeconds(3600));

        assertThatCode(() -> sut.requireSessionInCommunity(ORG, 5L, COMMUNITY)).doesNotThrowAnyException();
    }

    @Test
    void anUpcomingSessionIsAValidCheckpoint() {

        sessionIs(5L, COMMUNITY, "SCHEDULED", NOW.plusSeconds(1));

        assertThatCode(() -> sut.requireUpcomingSessionInCommunity(ORG, 5L, COMMUNITY)).doesNotThrowAnyException();
    }

    @Test
    void aCheckpointInAnotherCommunityIsNotFoundBeforeItsStatusIsEvenConsidered() {

        sessionIs(5L, 999L, "SCHEDULED", NOW.plusSeconds(3600));

        assertThatThrownBy(() -> sut.requireUpcomingSessionInCommunity(ORG, 5L, COMMUNITY))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aCancelledSessionCanAnswerNothing() {

        sessionIs(5L, COMMUNITY, "CANCELLED", NOW.plusSeconds(3600));

        assertThatThrownBy(() -> sut.requireUpcomingSessionInCommunity(ORG, 5L, COMMUNITY))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CHECKPOINT_NOT_UPCOMING");
    }

    @Test
    void aSkippedSessionCanAnswerNothing() {

        sessionIs(5L, COMMUNITY, "SKIPPED", NOW.plusSeconds(3600));

        assertThatThrownBy(() -> sut.requireUpcomingSessionInCommunity(ORG, 5L, COMMUNITY))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CHECKPOINT_NOT_UPCOMING");
    }

    @Test
    void anAlreadyHeldSessionCanAnswerNothingMore() {

        sessionIs(5L, COMMUNITY, "HELD", NOW.plusSeconds(3600));

        assertThatThrownBy(() -> sut.requireUpcomingSessionInCommunity(ORG, 5L, COMMUNITY))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CHECKPOINT_NOT_UPCOMING");
    }

    // The boundary: the hour a session was scheduled for has arrived, so it is no longer upcoming.
    @Test
    void aScheduledSessionWhoseHourHasExactlyArrivedIsNoLongerUpcoming() {

        sessionIs(5L, COMMUNITY, "SCHEDULED", NOW);

        assertThatThrownBy(() -> sut.requireUpcomingSessionInCommunity(ORG, 5L, COMMUNITY))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("CHECKPOINT_NOT_UPCOMING");
    }
}
