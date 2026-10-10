package dev.axonia.events.adapter.in.web;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.FakeRateLimitStore;
import dev.axonia.events.application.CalendarFeedRateLimiter;
import dev.axonia.events.application.CalendarSubscriptionService;
import dev.axonia.events.application.CalendarSubscriptionServiceTest;
import dev.axonia.events.domain.CalendarFeed;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.ratelimit.RateLimiters;
import dev.axonia.shared.api.web.ClientAddress;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/**
 * The anonymous feed. Two properties carry this endpoint and both are asserted here rather than assumed: every refusal
 * answers the same 404 with the same body, so a forged link cannot be told apart from a revoked one; and every fetch is
 * stamped, including one answered {@code 304}, because a client that keeps asking is a subscription that is alive.
 *
 * <p>
 * The four refusals themselves - unknown token, revoked token, departed member, non-{@code ACTIVE} organization - are
 * decided below this layer and pinned by {@link CalendarSubscriptionServiceTest}. What this class proves is that the
 * HTTP layer adds nothing that would tell them apart.
 */
@CoreWebMvcTest(controllers = PublicCalendarController.class)
@Import({CalendarFeedRateLimiter.class, PublicCalendarControllerTest.FeedConfig.class})
class PublicCalendarControllerTest extends ControllerTestSupport {

    private static final String URI = "/public/calendar/{token}.ics";

    private static final String ICS = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n";
    private static final Instant NOW = Instant.parse("2026-08-15T10:15:00Z");
    private static final String AGENT = "Outlook/16.0";

    @MockitoBean
    private CalendarSubscriptionService calendarSubscriptionService;

    @Test
    void feed_servesTheDocumentAsACalendarWithAStrongEtag() {

        // given
        var token = aToken("serves");
        givenAFeedFor(token);

        // when
        var actual = fetch(token);

        // then
        actual.assertThat().hasStatus(HttpStatus.OK).hasContentType("text/calendar;charset=UTF-8")
                .hasHeader(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"Frontend Dojo.ics\"; filename*=UTF-8''Frontend%20Dojo.ics")
                .hasBodyTextEqualTo(ICS);
        assertThat(etagOf(actual)).startsWith("\"").endsWith("\"");
    }

    /**
     * The application-wide default is {@code no-store}, which would ask a client to keep nothing of the response - the
     * ETag included - and leave the 304 below unreachable in practice.
     */
    @Test
    void feed_asksClientsToRevalidateRatherThanToKeepNothing() {

        // given
        var token = aToken("revalidate");
        givenAFeedFor(token);

        // when
        var actual = fetch(token);

        // then
        actual.assertThat().hasHeader(HttpHeaders.CACHE_CONTROL, "no-cache, private");
    }

    @Test
    void feed_worksWithNoSignInAtAll() {

        // given
        var token = aToken("anonymous");
        givenAFeedFor(token);

        // when
        var actual = fetch(token);

        // then
        actual.assertThat().hasStatus(HttpStatus.OK);
    }

    /** The rolling window is computed per request, so the instant the document is rendered at is the fetch's own. */
    @Test
    void feed_asksForTheDocumentAtTheInstantOfTheFetch() {

        // given
        var token = aToken("instant");
        givenAFeedFor(token);

        // when
        fetch(token);

        // then
        verify(calendarSubscriptionService).feed(token, NOW);
    }

    @Test
    void feed_anUnknownTokenIs404() {

        // given
        var token = aToken("unknown");
        givenARefusalFor(token);

        // when
        var actual = fetch(token);

        // then
        actual.assertThat().hasStatus(HttpStatus.NOT_FOUND);
    }

    /**
     * The security property of the whole endpoint. All four refusals arrive here as one exception carrying one message,
     * so what is left to prove is that the HTTP layer adds no cause of its own: the whole body, asserted to the byte,
     * is a bare 404 problem naming the request and nothing else, and no header a served document would carry appears
     * beside it. A {@code code}, a hint in the detail, or a lingering {@code ETag} would turn this feed into an oracle
     * for whether a link ever existed.
     */
    @Test
    void feed_aRefusalCarriesNothingThatNamesItsCause() throws Exception {

        // given
        var token = aToken("refusal");
        givenARefusalFor(token);

        // when
        var actual = fetch(token);

        // then
        actual.assertThat().hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .doesNotContainHeader(HttpHeaders.ETAG).doesNotContainHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(actual.getResponse().getContentAsString())
                .isEqualTo("{\"detail\":\"Calendar not found\",\"instance\":\"/public/calendar/" + token
                        + ".ics\",\"status\":404,\"title\":\"Not Found\"}");
    }

    @Test
    void feed_answers304WhenTheClientAlreadyHasThatEtag() {

        // given
        var token = aToken("notmodified");
        givenAFeedFor(token);
        var etag = etagOf(fetch(token));

        // when
        var actual = fetchHolding(token, etag);

        // then
        actual.assertThat().hasStatus(HttpStatus.NOT_MODIFIED).hasBodyTextEqualTo("")
                .hasHeader(HttpHeaders.CACHE_CONTROL, "no-cache, private");
    }

    @Test
    void feed_answers200WhenTheClientHoldsAStaleEtag() {

        // given
        var token = aToken("stale");
        givenAFeedFor(token);

        // when
        var actual = fetchHolding(token, "\"a-document-that-has-since-changed\"");

        // then
        actual.assertThat().hasStatus(HttpStatus.OK).hasBodyTextEqualTo(ICS);
    }

    /**
     * RFC 9110 requires the weak comparison function for {@code GET}, so a client echoing the validator back with a
     * {@code W/} prefix has still matched. A hand-rolled string equality answers 200 here and re-sends a whole schedule
     * every hour for as long as the subscription lives.
     */
    @Test
    void feed_answers304ForAWeakFormOfTheSameValidator() {

        // given
        var token = aToken("weak");
        givenAFeedFor(token);
        var etag = etagOf(fetch(token));

        // when
        var actual = fetchHolding(token, "W/" + etag);

        // then
        actual.assertThat().hasStatus(HttpStatus.NOT_MODIFIED).hasBodyTextEqualTo("");
    }

    /** {@code If-None-Match} is a list, and a client that has held several versions of this feed may send one. */
    @Test
    void feed_answers304WhenItsValidatorIsOneOfSeveralTheClientOffers() {

        // given
        var token = aToken("list");
        givenAFeedFor(token);
        var etag = etagOf(fetch(token));

        // when
        var actual = fetchHolding(token, "\"an-older-document\", " + etag);

        // then
        actual.assertThat().hasStatus(HttpStatus.NOT_MODIFIED).hasBodyTextEqualTo("");
    }

    /**
     * A header value is one line, and a community name is whatever its members typed. Writing one takes an
     * authenticated editor inside the organization, so this is defence in depth rather than an open hole - but an
     * anonymous surface is the wrong place for that distinction to be doing the work.
     */
    @Test
    void feed_neverCarriesACommunityNamesControlCharactersIntoAHeader() {

        // given
        var token = aToken("injected");
        when(calendarSubscriptionService.feed(token, NOW)).thenReturn(new CalendarFeed("Dojo\r\nX-Injected: yes", ICS));

        // when
        var actual = fetch(token);

        // then
        assertThat(actual.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo(
                "inline; filename=\"DojoX-Injected: yes.ics\"; " + "filename*=UTF-8''DojoX-Injected%3A%20yes.ics");
    }

    @Test
    void feed_stampsTheFetchWithTheClientThatMadeIt() {

        // given
        var token = aToken("stamps");
        givenAFeedFor(token);

        // when
        fetch(token);

        // then
        verify(calendarSubscriptionService).recordFetch(token, AGENT);
    }

    /** A 304 is still proof the subscription is alive, and the dialog's last-synced line has to say so. */
    @Test
    void feed_stampsTheFetchEvenWhenTheAnswerIsNotModified() {

        // given
        var token = aToken("stamps304");
        givenAFeedFor(token);
        var etag = etagOf(fetch(token));

        // when
        fetchHolding(token, etag).assertThat().hasStatus(HttpStatus.NOT_MODIFIED);

        // then
        verify(calendarSubscriptionService, times(2)).recordFetch(token, AGENT);
    }

    @Test
    void feed_stampsNothingForATokenThatResolvesToNoSubscription() {

        // given
        var token = aToken("nostamp");
        givenARefusalFor(token);

        // when
        fetch(token);

        // then
        verify(calendarSubscriptionService, never()).recordFetch(any(), any());
    }

    @Test
    void feed_rateLimitsAClientPollingOneTokenUnattended() {

        // given
        var token = aToken("flood");
        givenAFeedFor(token);
        for (var poll = 0; poll < 10; poll++) {
            fetch(token).assertThat().hasStatus(HttpStatus.OK);
        }

        // when
        var actual = fetch(token);

        // then - a capacity-10 bucket refilling once per minute needs 6s for its next token
        actual.assertThat().hasStatus(HttpStatus.TOO_MANY_REQUESTS).hasHeader(HttpHeaders.RETRY_AFTER, "6");
    }

    @Test
    void feed_aFloodOnOneTokenLeavesEveryOtherSubscriptionUnaffected() {

        // given
        var flooded = aToken("floodone");
        var bystander = aToken("bystander");
        givenAFeedFor(flooded);
        givenAFeedFor(bystander);
        for (var poll = 0; poll < 11; poll++) {
            fetch(flooded);
        }

        // when
        var actual = fetch(bystander);

        // then
        actual.assertThat().hasStatus(HttpStatus.OK);
    }

    /**
     * The budget an attacker cannot opt out of. A per-subscription bucket caps a client hammering one real URL, which
     * is the honest client's failure mode rather than the abusive one: a script picks a fresh random token per request,
     * shares no bucket with itself, and is never refused - while every one of its requests still costs a shared-store
     * round trip and an indexed lookup on an endpoint that needs no credential to reach. The caller's own address is
     * the one key it does not choose, so that budget is spent first.
     */
    @Test
    void feed_rateLimitsAFloodFromOneCallerEvenWhenEveryTokenIsDifferent() {

        // given
        var script = "198.51.100.77";
        for (var poll = 0; poll < 60; poll++) {
            var rotating = aToken("rotating" + poll);
            givenAFeedFor(rotating);
            fetchFrom(script, rotating).assertThat().hasStatus(HttpStatus.OK);
        }

        // when - one more, on a token no bucket has ever seen
        var fresh = aToken("rotating60");
        givenAFeedFor(fresh);
        var actual = fetchFrom(script, fresh);

        // then - a capacity-60 bucket refilling once per minute needs 1s for its next token
        actual.assertThat().hasStatus(HttpStatus.TOO_MANY_REQUESTS).hasHeader(HttpHeaders.RETRY_AFTER, "1");
    }

    /** The address budget is spent before the token is looked up too, so neither refusal leaks resolvability. */
    @Test
    void feed_aCallerRefusedOnItsAddressNeverReachesTheSubscription() {

        // given
        var script = "198.51.100.78";
        for (var poll = 0; poll < 60; poll++) {
            var rotating = aToken("scripted" + poll);
            givenAFeedFor(rotating);
            fetchFrom(script, rotating);
        }
        clearInvocations(calendarSubscriptionService);

        // when
        fetchFrom(script, aToken("scripted60")).assertThat().hasStatus(HttpStatus.TOO_MANY_REQUESTS);

        // then
        verifyNoInteractions(calendarSubscriptionService);
    }

    /** A refused poll must not reach the token lookup, or the limiter would be paying for the flood it refuses. */
    @Test
    void feed_aRateLimitedPollNeverReachesTheSubscription() {

        // given
        var token = aToken("refused");
        givenAFeedFor(token);
        for (var poll = 0; poll < 10; poll++) {
            fetch(token);
        }
        clearInvocations(calendarSubscriptionService);

        // when
        fetch(token).assertThat().hasStatus(HttpStatus.TOO_MANY_REQUESTS);

        // then
        verifyNoInteractions(calendarSubscriptionService);
    }

    /**
     * A distinct token per test, 43 base64url characters like {@code SubscriptionTokens.mint()} hands out. Both of the
     * limiter's budgets live in one store bean shared by the whole class, so two tests reusing either a token or a
     * caller address would spend one budget between them.
     */
    private static String aToken(String label) {

        return (label + "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJ").substring(0, 43);
    }

    /**
     * The caller behind one subscription's own polls, derived from its token so that a test giving itself a distinct
     * token gets a distinct address bucket for free. The one test that needs many tokens to arrive from a single caller
     * names that caller itself.
     */
    private static String theCallerOf(String token) {

        return "caller-of-" + token;
    }

    private void givenAFeedFor(String token) {

        when(calendarSubscriptionService.feed(token, NOW)).thenReturn(new CalendarFeed("Frontend Dojo", ICS));
    }

    private void givenARefusalFor(String token) {

        when(calendarSubscriptionService.feed(token, NOW)).thenThrow(new NotFoundException("Calendar not found"));
    }

    private MvcTestResult fetch(String token) {

        return fetchFrom(theCallerOf(token), token);
    }

    private MvcTestResult fetchFrom(String address, String token) {

        return mvc.get().uri(URI, token).with(anonymous()).with(from(address)).header(HttpHeaders.USER_AGENT, AGENT)
                .exchange();
    }

    private MvcTestResult fetchHolding(String token, String etag) {

        return mvc.get().uri(URI, token).with(anonymous()).with(from(theCallerOf(token)))
                .header(HttpHeaders.USER_AGENT, AGENT).header(HttpHeaders.IF_NONE_MATCH, etag).exchange();
    }

    private static RequestPostProcessor from(String address) {

        return request -> {
            request.addHeader(ClientAddress.HEADER, address);
            return request;
        };
    }

    private static String etagOf(MvcTestResult result) {

        return result.getResponse().getHeader(HttpHeaders.ETAG);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FeedConfig {

        @Bean
        Clock clock() {

            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        RateLimiters rateLimiters() {

            return new RateLimiters(new FakeRateLimitStore(), new SimpleMeterRegistry());
        }
    }
}
