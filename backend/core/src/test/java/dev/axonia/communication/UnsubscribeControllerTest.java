package dev.axonia.communication;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.axonia.ClientAddress;
import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.FakeRateLimitStore;
import dev.axonia.OrganizationBoundAuthentication;
import dev.axonia.RateLimiters;
import dev.axonia.communication.internal.CommunicationConfig;
import dev.axonia.communication.internal.UnsubscribeRateLimiter;
import dev.axonia.communication.internal.UnsubscribeTokens;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/**
 * The one anonymous write this module exposes. Every scenario here is deliberately indistinguishable from the outside:
 * a good token, a stale (already-redeemed) one and outright garbage all answer {@code 204} - only the rate limiter, a
 * disclosed and expected control, ever answers otherwise.
 *
 * <p>
 * {@link UnsubscribeTokens} is a REAL bean here, not a mock - {@code @Import}ing {@link CommunicationConfig} gets a
 * fully-wired token signer from the same {@code axonia.communication.unsubscribe-secret} the application would use. A
 * token that verifies below is one this test signed with that exact bean and posted straight back to the controller, so
 * nothing here could pass if the controller read a differently-named query parameter than {@code OutboxDrain} mints, or
 * if minting and binding disagreed about URL encoding - a mocked {@code UnsubscribeTokens} would never have caught
 * either.
 */
@CoreWebMvcTest(controllers = UnsubscribeController.class)
@Import({CommunicationConfig.class, UnsubscribeTokens.class, UnsubscribeRateLimiter.class,
        UnsubscribeControllerTest.RateLimitersConfig.class})
class UnsubscribeControllerTest extends ControllerTestSupport {

    private static final String URI = "/public/notifications/unsubscribe";

    @Autowired
    private UnsubscribeTokens tokens;

    @MockitoBean
    private NotificationPreferenceService notificationPreferenceService;

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    @Test
    void unsubscribe_aValidToken_flipsThePreferenceAndAnswers204() {

        // given
        var token = tokens.sign("user-1", "acme", MessageType.SESSION_WRAP_UP);

        // when
        var actual = unsubscribeFrom("198.51.100.1", token);

        // then
        actual.assertThat().hasStatus(HttpStatus.NO_CONTENT);
        verify(notificationPreferenceService).unsubscribe("acme", "user-1", MessageType.SESSION_WRAP_UP);
    }

    /**
     * A token that never verified - garbage, tampered, or simply belonging to nobody - must look identical from the
     * outside to one that verified and was applied: {@code 204}, and nothing written.
     */
    @Test
    void unsubscribe_aTokenThatDoesNotVerify_stillAnswers204ButWritesNothing() {

        // when
        var actual = unsubscribeFrom("198.51.100.2", "not-a-real-token");

        // then
        actual.assertThat().hasStatus(HttpStatus.NO_CONTENT);
        verifyNoInteractions(notificationPreferenceService);
    }

    /**
     * A token already redeemed once flips the same (already-false) preference again and answers {@code 204} again -
     * indistinguishable from the first click. There is nothing to stub differently the second time: the service call
     * itself is naturally idempotent, which is the whole mechanism this test pins down.
     */
    @Test
    void unsubscribe_aTokenUsedTwice_answers204BothTimesIdentically() {

        // given
        var token = tokens.sign("user-1", "acme", MessageType.SESSION_WRAP_UP);

        // when
        var first = unsubscribeFrom("198.51.100.3", token);
        var second = unsubscribeFrom("198.51.100.3", token);

        // then
        first.assertThat().hasStatus(HttpStatus.NO_CONTENT);
        second.assertThat().hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void unsubscribe_worksWithNoSignInAtAll() {

        // given
        var token = tokens.sign("user-1", "acme", MessageType.SESSION_WRAP_UP);

        // when
        var actual = unsubscribeFrom("198.51.100.4", token);

        // then
        actual.assertThat().hasStatus(HttpStatus.NO_CONTENT);
    }

    /**
     * The one piece of security-critical wiring in this controller: {@code OrganizationScope.runAs} must have set an
     * {@code app.current_org}-bearing authentication on the {@code SecurityContext} BEFORE
     * {@code notificationPreferenceService.unsubscribe} is ever called, since RLS reads it when the write's transaction
     * acquires its connection. A later refactor that inlined the call and dropped {@code runAs} would keep every other
     * test in this class green and fail only in production, with the database refusing the write - this is the test
     * that would catch it instead, by observing the SecurityContext from inside the mocked call itself.
     */
    @Test
    void unsubscribe_runsTheWriteInsideTheClaimedOrganizationsRlsScope() {

        // given
        var token = tokens.sign("user-1", "acme", MessageType.SESSION_WRAP_UP);
        var seenAuthentication = new AtomicReference<Authentication>();
        doAnswer(invocation -> {
            seenAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
            return null;
        }).when(notificationPreferenceService).unsubscribe("acme", "user-1", MessageType.SESSION_WRAP_UP);

        // when
        unsubscribeFrom("198.51.100.9", token).assertThat().hasStatus(HttpStatus.NO_CONTENT);

        // then
        assertThat(seenAuthentication.get()).isInstanceOf(OrganizationBoundAuthentication.class);
        assertThat(((OrganizationBoundAuthentication) seenAuthentication.get()).organizationId()).isEqualTo("acme");
    }

    @Test
    void unsubscribe_rateLimitsRepeatedAttemptsFromOneAddress() {

        // given
        for (var attempt = 0; attempt < 10; attempt++) {
            unsubscribeFrom("198.51.100.5", "guess").assertThat().hasStatus(HttpStatus.NO_CONTENT);
        }

        // when
        var actual = unsubscribeFrom("198.51.100.5", "guess");

        // then - a capacity-10 bucket refilling once per minute needs 6s for its next token
        actual.assertThat().hasStatus(HttpStatus.TOO_MANY_REQUESTS).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader("Retry-After", "6");
    }

    @Test
    void unsubscribe_aFloodToOneAddressLeavesAnotherAddressUnaffected() {

        // given - one address spends its entire budget
        for (var attempt = 0; attempt < 10; attempt++) {
            unsubscribeFrom("198.51.100.6", "guess").assertThat().hasStatus(HttpStatus.NO_CONTENT);
        }
        unsubscribeFrom("198.51.100.6", "guess").assertThat().hasStatus(HttpStatus.TOO_MANY_REQUESTS);

        // when - an unrelated caller arrives
        var actual = unsubscribeFrom("198.51.100.7", "guess");

        // then
        actual.assertThat().hasStatus(HttpStatus.NO_CONTENT);
    }

    /** A rate-limited caller's attempt must never reach the token check or the write. */
    @Test
    void unsubscribe_aRateLimitedAttemptNeverTouchesThePreferenceService() {

        // given
        for (var attempt = 0; attempt < 10; attempt++) {
            unsubscribeFrom("198.51.100.8", "guess").assertThat().hasStatus(HttpStatus.NO_CONTENT);
        }

        // when
        unsubscribeFrom("198.51.100.8", "guess").assertThat().hasStatus(HttpStatus.TOO_MANY_REQUESTS);

        // then
        verify(notificationPreferenceService, never()).unsubscribe(any(), any(), any());
    }

    private MvcTestResult unsubscribeFrom(String address, String token) {

        return mvc.post().uri(URI).with(anonymous()).with(from(address))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("token", token).exchange();
    }

    private static RequestPostProcessor from(String address) {

        return request -> {
            request.addHeader(ClientAddress.HEADER, address);
            return request;
        };
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RateLimitersConfig {

        @Bean
        RateLimiters rateLimiters() {

            return new RateLimiters(new FakeRateLimitStore(), new SimpleMeterRegistry());
        }
    }
}
