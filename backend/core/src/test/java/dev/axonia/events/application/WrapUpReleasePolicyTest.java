package dev.axonia.events.application;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.CommunicationSettings;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionRecordMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@MockitoSettings
class WrapUpReleasePolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");
    private static final Duration WINDOW = Duration.ofDays(30);

    @Mock
    private CommunityAccess communityAccess;

    private WrapUpReleasePolicy sut;

    @BeforeEach
    void wire() {

        lenient().when(communityAccess.communicationSettings(7L))
                .thenReturn(new CommunicationSettings(true, true, 3, 48));
        sut = new WrapUpReleasePolicy(communityAccess, () -> WINDOW);
    }

    @Test
    void yesterdaysSession_releasesItsWrapUpAfterTheCommunityGracePeriod_byDefault() {

        var releaseAt = sut.atClose(heldAt(NOW.minus(1, ChronoUnit.DAYS)), null, NOW);

        assertThat(releaseAt).isEqualTo(NOW.plus(48, ChronoUnit.HOURS));
    }

    @Test
    void aSessionOlderThanTheWrapUpWindow_isClosedWithNoWrapUp_byDefault() {

        var releaseAt = sut.atClose(heldAt(NOW.minus(WINDOW).minusSeconds(1)), null, NOW);

        assertThat(releaseAt).as("a backfilled session must not mail the community about something long over").isNull();
    }

    @Test
    void aSessionExactlyAtTheWindowEdge_stillReleasesItsWrapUp_byDefault() {

        var releaseAt = sut.atClose(heldAt(NOW.minus(WINDOW)), null, NOW);

        assertThat(releaseAt).isEqualTo(NOW.plus(48, ChronoUnit.HOURS));
    }

    @Test
    void anExplicitNo_suppressesTheWrapUp_evenForYesterdaysSession() {

        assertThat(sut.atClose(heldAt(NOW.minus(1, ChronoUnit.DAYS)), false, NOW)).isNull();
    }

    @Test
    void anExplicitYes_releasesTheWrapUp_evenForAnOldSession() {

        var releaseAt = sut.atClose(heldAt(NOW.minus(90, ChronoUnit.DAYS)), true, NOW);

        assertThat(releaseAt).isEqualTo(NOW.plus(48, ChronoUnit.HOURS));
    }

    @Test
    void recordsHistory_onlyBeyondTheWrapUpWindow() {

        assertThat(sut.recordsHistory(heldAt(NOW.minus(WINDOW).minusSeconds(1)), NOW)).isTrue();
        assertThat(sut.recordsHistory(heldAt(NOW.minus(WINDOW)), NOW)).isFalse();
        assertThat(sut.recordsHistory(heldAt(NOW.minus(1, ChronoUnit.HOURS)), NOW)).isFalse();
    }

    private static SessionRecord heldAt(Instant scheduledAt) {

        return SessionRecordMother.any().id(42L).communityId(7L).scheduledAt(scheduledAt).build();
    }
}
