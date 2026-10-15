package dev.axonia.health.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.health.api.spi.ArtifactSignals;
import dev.axonia.health.api.spi.EventsSignals;
import dev.axonia.health.api.spi.MembershipSignals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class ActivityTraceServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-11T12:00:00Z");
    private static final Instant FROM = NOW.minus(Duration.ofDays(90));

    @Mock
    private EventsSignals eventsSignals;

    @Mock
    private ArtifactSignals artifactSignals;

    @Mock
    private MembershipSignals membershipSignals;

    private ActivityTraceService sut() {

        return new ActivityTraceService(eventsSignals, artifactSignals, membershipSignals,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void trace_readsThreeSeriesOverTheLastNinetyDays() {

        // given
        var additions = series(3, 2L);
        var joins = series(89, 5L);
        when(membershipSignals.communityIdForSlug("backend-guild")).thenReturn(7L);
        when(eventsSignals.heldSessionDates(7L, FROM, NOW)).thenReturn(List.of(NOW.minusSeconds(60)));
        when(artifactSignals.artifactsPerBucket(7L, FROM, NOW, 90)).thenReturn(additions);
        when(membershipSignals.joinsPerBucket(7L, FROM, NOW, 90)).thenReturn(joins);

        // when
        var actual = sut().trace("backend-guild");

        // then
        assertThat(actual.days()).isEqualTo(90);
        assertThat(actual.sessionsHeld()).hasSize(90).last().isEqualTo(1L);
        assertThat(actual.libraryAdditions()).isEqualTo(additions);
        assertThat(actual.joins()).isEqualTo(joins);
    }

    @Test
    void trace_neverAsksWhetherTheCallerMayViewHealth() {

        // given
        when(membershipSignals.communityIdForSlug("backend-guild")).thenReturn(7L);
        when(eventsSignals.heldSessionDates(7L, FROM, NOW)).thenReturn(List.of());
        when(artifactSignals.artifactsPerBucket(7L, FROM, NOW, 90)).thenReturn(series(0, 0L));
        when(membershipSignals.joinsPerBucket(7L, FROM, NOW, 90)).thenReturn(series(0, 0L));

        // when
        sut().trace("backend-guild");

        // then
        verify(membershipSignals, never()).canViewHealth(anyLong(), anyString());
    }

    private static List<Long> series(int day, long count) {

        var series = new ArrayList<>(Collections.nCopies(90, 0L));
        series.set(day, count);
        return List.copyOf(series);
    }
}
