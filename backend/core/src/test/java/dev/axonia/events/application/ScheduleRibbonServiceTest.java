package dev.axonia.events.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.api.RibbonTick;
import dev.axonia.events.api.ScheduleEntry;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionListing;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class ScheduleRibbonServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    @Mock
    private SessionListing sessionListing;

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private OrganizationContext organizationContext;

    private ScheduleRibbonService sut;

    @BeforeEach
    void wire() {

        sut = new ScheduleRibbonService(sessionListing, scheduleService, communityAccess, organizationContext,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void aWindowAcrossNowReadsMemoriesBeforeItAndTheScheduleAfterIt() {

        // given
        given();
        var from = Instant.parse("2026-09-01T00:00:00Z");
        var to = Instant.parse("2026-12-01T00:00:00Z");
        when(sessionListing.instants("default", 5L, from, NOW))
                .thenReturn(List.of(Instant.parse("2026-09-10T18:00:00Z")));
        when(scheduleService.schedule(ahead(NOW, to))).thenReturn(List.of(
                entry("2026-11-10T18:00:00Z", SessionStatus.SCHEDULED, true),
                entry("2026-10-20T18:00:00Z", SessionStatus.SCHEDULED, false),
                entry("2026-10-21T18:00:00Z", SessionStatus.CANCELLED, false),
                entry("2026-10-22T18:00:00Z", SessionStatus.SKIPPED, true), entry(null, SessionStatus.SCHEDULED, false),
                entry("2026-10-16T08:00:00Z", SessionStatus.SCHEDULED, true)));
        when(sessionListing.firstSessionAt("default", 5L))
                .thenReturn(Optional.of(Instant.parse("2024-01-01T00:00:00Z")));

        // when
        var actual = sut.ribbon("frontend", from, to);

        // then
        assertThat(actual.firstSessionAt()).isEqualTo(Instant.parse("2024-01-01T00:00:00Z"));
        assertThat(actual.ticks()).containsExactly(
                new RibbonTick(Instant.parse("2026-09-10T18:00:00Z"), RibbonTick.Kind.MEMORY),
                new RibbonTick(Instant.parse("2026-10-20T18:00:00Z"), RibbonTick.Kind.UPCOMING),
                new RibbonTick(Instant.parse("2026-11-10T18:00:00Z"), RibbonTick.Kind.PROJECTED));
    }

    @Test
    void aWindowEntirelyInThePastNeverAsksTheSchedule() {

        // given
        given();
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-02-01T00:00:00Z");
        when(sessionListing.instants("default", 5L, from, to))
                .thenReturn(List.of(Instant.parse("2026-01-10T18:00:00Z")));
        when(sessionListing.firstSessionAt("default", 5L)).thenReturn(Optional.empty());

        // when
        var actual = sut.ribbon("frontend", from, to);

        // then
        assertThat(actual.firstSessionAt()).isNull();
        assertThat(actual.ticks()).extracting(RibbonTick::kind).containsExactly(RibbonTick.Kind.MEMORY);
        verifyNoInteractions(scheduleService);
    }

    @Test
    void aWindowEntirelyInTheFutureNeverReadsMemories() {

        // given
        given();
        var from = Instant.parse("2027-01-01T00:00:00Z");
        var to = Instant.parse("2027-02-01T00:00:00Z");
        when(scheduleService.schedule(ahead(from, to)))
                .thenReturn(List.of(entry("2027-01-10T18:00:00Z", SessionStatus.SCHEDULED, true)));
        when(sessionListing.firstSessionAt("default", 5L)).thenReturn(Optional.empty());

        // when
        var actual = sut.ribbon("frontend", from, to);

        // then
        assertThat(actual.ticks()).extracting(RibbonTick::kind).containsExactly(RibbonTick.Kind.PROJECTED);
        verify(sessionListing, never()).instants(any(), anyLong(), any(), any());
    }

    @Test
    void aWindowThatDoesNotEndAfterItStartsIsRefused() {

        var at = Instant.parse("2026-10-01T00:00:00Z");

        assertThatThrownBy(() -> sut.ribbon("frontend", at, at)).isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "RIBBON_WINDOW_EMPTY");
        verifyNoInteractions(sessionListing, scheduleService);
    }

    @Test
    void aWindowWiderThanFifteenHundredDaysIsRefused() {

        var from = Instant.parse("2022-01-01T00:00:00Z");

        assertThatThrownBy(() -> sut.ribbon("frontend", from, from.plus(Duration.ofDays(1501))))
                .isInstanceOf(ValidationException.class).hasFieldOrPropertyWithValue("code", "RIBBON_WINDOW_TOO_WIDE");
        verifyNoInteractions(sessionListing, scheduleService);
    }

    @Test
    void aWindowOfExactlyFifteenHundredDaysIsRead() {

        // given
        given();
        var from = Instant.parse("2023-01-01T00:00:00Z");
        var to = from.plus(Duration.ofDays(1500));
        when(sessionListing.instants("default", 5L, from, NOW)).thenReturn(List.of());
        when(scheduleService.schedule(ahead(NOW, to))).thenReturn(List.of());
        when(sessionListing.firstSessionAt("default", 5L)).thenReturn(Optional.empty());

        // when
        var actual = sut.ribbon("frontend", from, to);

        // then
        assertThat(actual.ticks()).isEmpty();
    }

    private void given() {

        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(5L);
    }

    private static ScheduleQuery ahead(Instant from, Instant to) {

        return ScheduleQuery.builder().organizationId("default").communityId(5L).from(from).to(to).now(NOW).build();
    }

    private static ScheduleEntry entry(String at, SessionStatus status, boolean virtual) {

        return ScheduleEntry.builder().scheduledAt(at == null ? null : Instant.parse(at)).status(status)
                .virtual(virtual).build();
    }
}
