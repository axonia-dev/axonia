package dev.axonia.events.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.context.SecurityContextHolder;

import dev.axonia.community.api.CommunityIdentity;
import dev.axonia.events.api.ScheduleEntry;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.application.port.out.CalendarSubscriptionStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationAccess;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.domain.CalendarSubscriptionRecord;
import dev.axonia.events.domain.SubscriptionTokens;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.tenancy.OrganizationBoundAuthentication;
import dev.axonia.shared.configuration.PublicUrlProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
public class CalendarSubscriptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T10:15:00Z");
    private static final String LAST_AGENT = "Outlook/16.0";

    @Mock
    private CalendarSubscriptionStore repository;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private OrganizationAccess organizationAccess;
    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private ScheduleService scheduleService;

    private CalendarSubscriptionService sut;

    @BeforeEach
    void wire() {

        sut = new CalendarSubscriptionService(repository, communityAccess, organizationAccess, organizationContext,
                currentUser, scheduleService, new PublicUrlProperties("https://axonia.example"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void clearScope() {

        SecurityContextHolder.clearContext();
    }

    private void signedInAsAliceOfDojo() {

        when(organizationContext.organizationId()).thenReturn("northwind");
        when(communityAccess.communityIdForSlug("dojo")).thenReturn(12L);
        when(currentUser.userId()).thenReturn("alice");
    }

    private void signedInAsAliceOfDojoWhoIsAMember() {

        signedInAsAliceOfDojo();
        when(communityAccess.isMember(12L, "alice")).thenReturn(true);
    }

    private static CalendarSubscriptionRecord existing() {

        return CalendarSubscriptionRecord.builder().id(7L).organizationId("northwind").communityId(12L).userId("alice")
                .tokenHash("old-hash").lastFetchedAt(Instant.parse("2026-08-14T08:00:00Z")).lastFetchedAgent(LAST_AGENT)
                .version(0L).build();
    }

    /**
     * A subscription is a per-member feed; a non-member subscribing must not create a row for a feed with nothing in
     * it.
     */
    @Test
    void subscribe_refusesANonMember() {

        // given
        signedInAsAliceOfDojo();
        when(communityAccess.isMember(12L, "alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.subscribe("dojo")).isInstanceOf(ForbiddenException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void subscribe_mintsAUrlTheMemberCanPasteIntoACalendar() {

        // given
        signedInAsAliceOfDojoWhoIsAMember();
        when(repository.findByOrganizationIdAndCommunityIdAndUserId("northwind", 12L, "alice"))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var actual = sut.subscribe("dojo");

        // then
        assertThat(actual.url()).matches("https://axonia\\.example/public/calendar/[A-Za-z0-9_-]{43}\\.ics");
        assertThat(actual.lastFetchedAt()).isNull();
        assertThat(actual.lastFetchedAgent()).isNull();
    }

    @Test
    void subscribe_storesOnlyTheHashOfTheTokenItHandedOut() {

        // given
        signedInAsAliceOfDojoWhoIsAMember();
        when(repository.findByOrganizationIdAndCommunityIdAndUserId("northwind", 12L, "alice"))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var actual = sut.subscribe("dojo");

        // then
        var saved = ArgumentCaptor.forClass(CalendarSubscriptionRecord.class);
        verify(repository).save(saved.capture());
        var token = actual.url().replace("https://axonia.example/public/calendar/", "").replace(".ics", "");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SubscriptionTokens.hash(token)).isNotEqualTo(token);
        assertThat(saved.getValue().getOrganizationId()).isEqualTo("northwind");
        assertThat(saved.getValue().getCommunityId()).isEqualTo(12L);
        assertThat(saved.getValue().getUserId()).isEqualTo("alice");
    }

    @Test
    void subscribe_isFindOrCreateAndCannotShowAnAlreadyIssuedTokenASecondTime() {

        // given
        signedInAsAliceOfDojoWhoIsAMember();
        when(repository.findByOrganizationIdAndCommunityIdAndUserId("northwind", 12L, "alice"))
                .thenReturn(Optional.of(existing()));

        // when
        var actual = sut.subscribe("dojo");

        // then
        assertThat(actual.url()).isNull();
        assertThat(actual.lastFetchedAt()).isEqualTo(Instant.parse("2026-08-14T08:00:00Z"));
        assertThat(actual.lastFetchedAgent()).isEqualTo(LAST_AGENT);
        verify(repository, never()).save(any());
    }

    @Test
    void reset_replacesTheHashAndClearsTheStampSoEveryCalendarHoldingTheOldUrlStops() {

        // given
        signedInAsAliceOfDojo();
        when(repository.findByOrganizationIdAndCommunityIdAndUserId("northwind", 12L, "alice"))
                .thenReturn(Optional.of(existing()));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var actual = sut.reset("dojo");

        // then
        var saved = ArgumentCaptor.forClass(CalendarSubscriptionRecord.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(7L);
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo("old-hash");
        assertThat(saved.getValue().getLastFetchedAt()).isNull();
        assertThat(saved.getValue().getLastFetchedAgent()).isNull();
        assertThat(actual.url()).matches("https://axonia\\.example/public/calendar/[A-Za-z0-9_-]{43}\\.ics");
        assertThat(actual.lastFetchedAt()).isNull();
    }

    @Test
    void reset_withoutASubscriptionToReset() {

        // given
        signedInAsAliceOfDojo();
        when(repository.findByOrganizationIdAndCommunityIdAndUserId("northwind", 12L, "alice"))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.reset("dojo")).isInstanceOf(NotFoundException.class)
                .hasMessage("Calendar not found");
    }

    @Test
    void current_reportsTheStampAndNeverTheUrl() {

        // given
        signedInAsAliceOfDojo();
        when(repository.findByOrganizationIdAndCommunityIdAndUserId("northwind", 12L, "alice"))
                .thenReturn(Optional.of(existing()));

        // when
        var actual = sut.current("dojo");

        // then
        assertThat(actual).isPresent().get().satisfies(subscription -> {
            assertThat(subscription.url()).isNull();
            assertThat(subscription.lastFetchedAgent()).isEqualTo(LAST_AGENT);
        });
    }

    @Test
    void current_whenTheMemberHasNeverSubscribed() {

        // given
        signedInAsAliceOfDojo();
        when(repository.findByOrganizationIdAndCommunityIdAndUserId("northwind", 12L, "alice"))
                .thenReturn(Optional.empty());

        // when / then
        assertThat(sut.current("dojo")).isEmpty();
    }

    @Test
    void feed_rendersTheCommunityScheduleOverTheRollingWindowStampedWithTheFetchInstant() {

        // given
        var token = subscribed();
        when(communityAccess.communityIdentity("northwind", 12L))
                .thenReturn(new CommunityIdentity("dojo", "Frontend Dojo", "#fff"));
        when(scheduleService.schedule(any()))
                .thenReturn(List.of(ScheduleEntry.builder().seriesId(12L).title("Frontend Dojo")
                        .occurrenceDate(LocalDate.of(2026, 8, 18)).scheduledAt(Instant.parse("2026-08-18T16:00:00Z"))
                        .hasTime(true).durationMinutes(60).status(SessionStatus.SCHEDULED).virtual(true).build()));

        // when
        var actual = sut.feed(token, NOW);

        // then
        var query = ArgumentCaptor.forClass(ScheduleQuery.class);
        verify(scheduleService).schedule(query.capture());
        assertThat(query.getValue().organizationId()).isEqualTo("northwind");
        assertThat(query.getValue().communityId()).isEqualTo(12L);
        assertThat(query.getValue().from()).isEqualTo(Instant.parse("2026-07-16T10:15:00Z"));
        assertThat(query.getValue().to()).isEqualTo(Instant.parse("2027-02-11T10:15:00Z"));
        assertThat(query.getValue().now()).isEqualTo(NOW);
        assertThat(actual.calendarName()).isEqualTo("Frontend Dojo");
        assertThat(actual.ics()).contains("X-WR-CALNAME:Frontend Dojo\r\n")
                .contains("UID:series-12-2026-08-18@axonia.example\r\n").contains("DTSTAMP:20260815T101500Z\r\n");
    }

    /**
     * The property the {@code ETag} is worthless without, and the one nothing on either side of the seam owned: the
     * controller hashes the body it just rendered, so a body that moves with the wall clock produces a fresh validator
     * on every poll and {@code If-None-Match} can never match. Two fetches of an unchanged schedule, hours apart, have
     * to be the same document down to the byte.
     */
    @Test
    void feed_rendersTheSameBytesForAnUnchangedScheduleHoweverMuchLaterItIsPolled() {

        // given
        var token = subscribed();
        when(communityAccess.communityIdentity("northwind", 12L))
                .thenReturn(new CommunityIdentity("dojo", "Frontend Dojo", "#fff"));
        when(scheduleService.schedule(any()))
                .thenReturn(List.of(ScheduleEntry.builder().seriesId(12L).title("Frontend Dojo")
                        .occurrenceDate(LocalDate.of(2026, 8, 18)).scheduledAt(Instant.parse("2026-08-18T16:00:00Z"))
                        .hasTime(true).durationMinutes(60).status(SessionStatus.SCHEDULED).virtual(true).version(0L)
                        .updatedAt(Instant.parse("2026-08-14T09:00:12Z")).build()));

        // when
        var first = sut.feed(token, NOW);
        var later = sut.feed(token, NOW.plus(6, ChronoUnit.HOURS));

        // then
        assertThat(later.ics()).isEqualTo(first.ics());
        assertThat(first.ics()).contains("DTSTAMP:20260814T090012Z\r\n");
    }

    /**
     * Everything below the token lookup reads tables the tenant's own policies cover, so the scope has to be entered
     * before the first of those reads rather than around any one of them.
     */
    @Test
    void feed_readsEverythingBelowTheTokenInsideTheOwningOrganizationsScope() {

        // given
        var token = subscribed();
        when(communityAccess.communityIdentity("northwind", 12L))
                .thenReturn(new CommunityIdentity("dojo", "Frontend Dojo", null));
        when(scheduleService.schedule(any())).thenAnswer(invocation -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            assertThat(authentication).isInstanceOf(OrganizationBoundAuthentication.class);
            assertThat(((OrganizationBoundAuthentication) authentication).organizationId()).isEqualTo("northwind");
            return List.of();
        });

        // when
        sut.feed(token, NOW);

        // then
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void feed_unknownToken() {

        // given
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.feed("forged", NOW)).isInstanceOf(NotFoundException.class)
                .hasMessage("Calendar not found");
    }

    @Test
    void feed_ownerHasLeftTheCommunity() {

        // given
        var token = SubscriptionTokens.mint();
        when(repository.findByTokenHash(SubscriptionTokens.hash(token))).thenReturn(Optional.of(existing()));
        when(organizationAccess.isActive("northwind")).thenReturn(true);
        when(communityAccess.isMember("northwind", 12L, "alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.feed(token, NOW)).isInstanceOf(NotFoundException.class)
                .hasMessage("Calendar not found");
    }

    @Test
    void feed_organizationIsNoLongerActive() {

        // given
        var token = SubscriptionTokens.mint();
        when(repository.findByTokenHash(SubscriptionTokens.hash(token))).thenReturn(Optional.of(existing()));
        when(organizationAccess.isActive("northwind")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.feed(token, NOW)).isInstanceOf(NotFoundException.class)
                .hasMessage("Calendar not found");
    }

    @Test
    void recordFetch_stampsFromTheClockInsideTheOwningOrganizationsScope() {

        // given
        var token = SubscriptionTokens.mint();
        when(repository.findByTokenHash(SubscriptionTokens.hash(token))).thenReturn(Optional.of(existing()));

        // when
        sut.recordFetch(token, LAST_AGENT);

        // then
        verify(repository).stampFetch(7L, NOW, LAST_AGENT);
    }

    @Test
    void recordFetch_trimsAnOverlongUserAgentToTheColumnsWidth() {

        // given
        var token = SubscriptionTokens.mint();
        when(repository.findByTokenHash(SubscriptionTokens.hash(token))).thenReturn(Optional.of(existing()));

        // when
        sut.recordFetch(token, "a".repeat(400));

        // then
        verify(repository).stampFetch(7L, NOW, "a".repeat(255));
    }

    @Test
    void recordFetch_withNoUserAgentToReport() {

        // given
        var token = SubscriptionTokens.mint();
        when(repository.findByTokenHash(SubscriptionTokens.hash(token))).thenReturn(Optional.of(existing()));

        // when
        sut.recordFetch(token, null);

        // then
        verify(repository).stampFetch(7L, NOW, null);
    }

    /**
     * A stamp is bookkeeping about a fetch, not part of one. Two calendar clients polling the same URL collide on the
     * row's version sooner or later, and the subscriber must not get a 500 because the second one lost the race.
     */
    @Test
    void recordFetch_aFailedStampNeverFailsTheFetchItReportsOn() {

        // given
        var token = SubscriptionTokens.mint();
        when(repository.findByTokenHash(SubscriptionTokens.hash(token))).thenReturn(Optional.of(existing()));
        doThrow(new OptimisticLockingFailureException("another client stamped first")).when(repository)
                .stampFetch(any(), any(), any());

        // when / then
        assertThatNoException().isThrownBy(() -> sut.recordFetch(token, LAST_AGENT));
    }

    @Test
    void recordFetch_unknownTokenStampsNothing() {

        // given
        when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

        // when
        sut.recordFetch("forged", LAST_AGENT);

        // then
        verify(repository, never()).stampFetch(any(), any(), any());
    }

    private String subscribed() {

        var token = SubscriptionTokens.mint();
        when(repository.findByTokenHash(SubscriptionTokens.hash(token))).thenReturn(Optional.of(existing()));
        when(organizationAccess.isActive("northwind")).thenReturn(true);
        when(communityAccess.isMember("northwind", 12L, "alice")).thenReturn(true);
        return token;
    }
}
