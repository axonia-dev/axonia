package dev.axonia.events.adapter.in.web;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.application.CalendarSubscriptionService;
import dev.axonia.events.domain.CalendarSubscription;
import dev.axonia.shared.api.error.NotFoundException;
import tools.jackson.core.JacksonException;

import static org.mockito.Mockito.when;

/**
 * The member's own three calls: read the subscription, obtain one, and invalidate the URL every calendar still holds.
 */
@CoreWebMvcTest(controllers = CalendarSubscriptionController.class)
class CalendarSubscriptionControllerTest extends ControllerTestSupport {

    private static final String URI = "/axonia/communities/{slug}/calendar-subscription";
    private static final String SLUG = "frontend-dojo";
    private static final String FEED_URL = "https://axonia.example/public/calendar/"
            + "hZ8mQ1r7TuVwXyZ0abcdefghijklmnopqrstuvwxyz0.ics";

    @MockitoBean
    private CalendarSubscriptionService calendarSubscriptionService;

    @Test
    void current_reportsTheStampAndNeverTheUrl() throws JacksonException {

        // given
        var subscription = new CalendarSubscription(null, Instant.parse("2026-08-15T08:00:00Z"), "Outlook/16.0");
        when(calendarSubscriptionService.current(SLUG)).thenReturn(Optional.of(subscription));

        // when
        var actual = mvc.get().uri(URI, SLUG);

        // then
        actual.assertThat().hasStatus(HttpStatus.OK).bodyJson().isEqualTo(jsonMapper.writeValueAsString(subscription));
    }

    @Test
    void current_whenTheMemberHasNeverSubscribed() {

        // given
        when(calendarSubscriptionService.current(SLUG)).thenReturn(Optional.empty());

        // when
        var actual = mvc.get().uri(URI, SLUG);

        // then
        actual.assertThat().hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void subscribe_handsBackTheUrlToPasteIntoACalendar() throws JacksonException {

        // given
        var subscription = new CalendarSubscription(FEED_URL, null, null);
        when(calendarSubscriptionService.subscribe(SLUG)).thenReturn(subscription);

        // when
        var actual = mvc.post().uri(URI, SLUG);

        // then
        actual.assertThat().hasStatus(HttpStatus.OK).bodyJson().isEqualTo(jsonMapper.writeValueAsString(subscription));
    }

    @Test
    void reset_handsBackAFreshUrlWithTheStampCleared() throws JacksonException {

        // given
        var subscription = new CalendarSubscription(FEED_URL, null, null);
        when(calendarSubscriptionService.reset(SLUG)).thenReturn(subscription);

        // when
        var actual = mvc.post().uri(URI + "/reset", SLUG);

        // then
        actual.assertThat().hasStatus(HttpStatus.OK).bodyJson().isEqualTo(jsonMapper.writeValueAsString(subscription));
    }

    @Test
    void reset_withoutASubscriptionToReset() {

        // given
        when(calendarSubscriptionService.reset(SLUG)).thenThrow(new NotFoundException("Calendar not found"));

        // when
        var actual = mvc.post().uri(URI + "/reset", SLUG);

        // then
        actual.assertThat().hasStatus(HttpStatus.NOT_FOUND);
    }
}
