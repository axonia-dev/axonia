package dev.axonia.agent.adapter.out.http;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

import dev.axonia.SteppableClock;
import dev.axonia.agent.application.ClientMetadata;
import dev.axonia.agent.configuration.McpOAuthProperties;
import tools.jackson.databind.json.JsonMapper;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpClientMetadataDocumentsTest {

    private static final String CLIENT_ID = "https://claude.ai/oauth/claude-code-client-metadata";
    private static final String DOCUMENT = """
            {"client_id":"https://claude.ai/oauth/claude-code-client-metadata","client_name":"Claude Code",
             "redirect_uris":["http://localhost/callback","http://127.0.0.1/callback"],
             "grant_types":["authorization_code","refresh_token"],"response_types":["code"],
             "token_endpoint_auth_method":"none"}""";
    private static final int CONCURRENT_CALLERS = 8;

    MockRestServiceServer server;
    SteppableClock clock;
    HttpClientMetadataDocuments sut;

    @BeforeEach
    void setUp() {

        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new SteppableClock(Instant.parse("2026-09-24T12:00:00Z"));
        sut = new HttpClientMetadataDocuments(builder.build(), JsonMapper.builder().build(),
                new McpOAuthProperties(Set.of("claude.ai")), clock);
    }

    @Test
    void resolve_readsAValidDocument() {

        // given
        server.expect(once(), requestTo(CLIENT_ID)).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(DOCUMENT, MediaType.APPLICATION_JSON));

        // when
        var actual = sut.resolve(CLIENT_ID);

        // then
        assertThat(actual).isPresent();
        assertThat(actual.get().clientName()).isEqualTo("Claude Code");
        assertThat(actual.get().clientHost()).isEqualTo("claude.ai");
        assertThat(actual.get().redirectUris()).containsExactly("http://localhost/callback",
                "http://127.0.0.1/callback");
    }

    @Test
    void resolve_refusesAClientIdOffTheAllowlistWithoutFetching() {

        // server has no expectation: any request fails the test in verify()
        assertThat(sut.resolve("https://evil.example/oauth/client")).isEmpty();
        assertThat(sut.resolve("https://claude.ai.evil.example/oauth/client")).isEmpty();
        assertThat(sut.resolve("http://claude.ai/oauth/client")).isEmpty();
        assertThat(sut.resolve("https://claude.ai:8443/oauth/client")).isEmpty();
        assertThat(sut.resolve("https://user@claude.ai/oauth/client")).isEmpty();
        assertThat(sut.resolve("https://claude.ai")).isEmpty();
        assertThat(sut.resolve("https://claude.ai/")).isEmpty();
        assertThat(sut.resolve("https://claude.ai/a?b=c")).isEmpty();
        assertThat(sut.resolve("https://claude.ai/a#b")).isEmpty();
        assertThat(sut.resolve("not a url")).isEmpty();
        assertThat(sut.resolve(null)).isEmpty();
        server.verify();
    }

    @Test
    void resolve_refusesAMismatchedClientId() {

        server.expect(requestTo(CLIENT_ID))
                .andRespond(withSuccess(
                        DOCUMENT.replace("\"client_id\":\"" + CLIENT_ID, "\"client_id\":\"https://claude.ai/x"),
                        MediaType.APPLICATION_JSON));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @Test
    void resolve_refusesAConfidentialClient() {

        server.expect(requestTo(CLIENT_ID)).andRespond(
                withSuccess(DOCUMENT.replace("\"none\"", "\"client_secret_basic\""), MediaType.APPLICATION_JSON));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @Test
    void resolve_refusesADocumentWithANonLoopbackRedirect() {

        server.expect(requestTo(CLIENT_ID))
                .andRespond(withSuccess(
                        DOCUMENT.replace("\"http://127.0.0.1/callback\"", "\"https://evil.example/callback\""),
                        MediaType.APPLICATION_JSON));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @Test
    void resolve_refusesADocumentWithoutTheCodeGrant() {

        server.expect(requestTo(CLIENT_ID))
                .andRespond(withSuccess(DOCUMENT.replace("\"authorization_code\",", ""), MediaType.APPLICATION_JSON));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @Test
    void resolve_refusesAMissingOrOverlongName() {

        server.expect(requestTo(CLIENT_ID)).andRespond(withSuccess(
                DOCUMENT.replace("\"Claude Code\"", "\"" + "x".repeat(101) + "\""), MediaType.APPLICATION_JSON));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @Test
    void resolve_refusesAnOversizedBody() {

        server.expect(requestTo(CLIENT_ID))
                .andRespond(withSuccess(
                        DOCUMENT.replace("\"response_types\"",
                                "\"pad\":\"" + "x".repeat(17_000) + "\",\"response_types\""),
                        MediaType.APPLICATION_JSON));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {5 * 1024 + 1, 16 * 1024})
    void resolve_refusesABodyOverFiveKilobytes(int bytes) {

        // given
        server.expect(once(), requestTo(CLIENT_ID))
                .andRespond(withSuccess(documentOfSize(bytes), MediaType.APPLICATION_JSON));

        // when
        var actual = sut.resolve(CLIENT_ID);

        // then
        assertThat(actual).isEmpty();
        server.verify();
    }

    @Test
    void resolve_readsABodyOfExactlyFiveKilobytes() {

        // given
        server.expect(once(), requestTo(CLIENT_ID))
                .andRespond(withSuccess(documentOfSize(5 * 1024), MediaType.APPLICATION_JSON));

        // when
        var actual = sut.resolve(CLIENT_ID);

        // then
        assertThat(actual).isPresent();
        server.verify();
    }

    @Test
    void resolve_refusesARedirectAndANon2xx() {

        server.expect(requestTo(CLIENT_ID))
                .andRespond(withStatus(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "https://claude.ai/elsewhere"));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {201, 203, 204, 206})
    void resolve_refusesASuccessOtherThan200(int status) {

        // given
        server.expect(once(), requestTo(CLIENT_ID)).andRespond(
                withStatus(HttpStatusCode.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(DOCUMENT));

        // when
        var actual = sut.resolve(CLIENT_ID);

        // then
        assertThat(actual).isEmpty();
        server.verify();
    }

    @Test
    void resolve_treatsATimeoutAsAFailure() {

        server.expect(requestTo(CLIENT_ID)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @Test
    void resolve_refusesARedirectThatIsNotAUri() {

        server.expect(requestTo(CLIENT_ID)).andRespond(
                withSuccess(DOCUMENT.replace("\"http://127.0.0.1/callback\"", "\"http://127.0.0.1/call back\""),
                        MediaType.APPLICATION_JSON));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @Test
    void resolve_refusesARedirectThatIsNotAString() {

        server.expect(requestTo(CLIENT_ID)).andRespond(
                withSuccess(DOCUMENT.replace("\"http://127.0.0.1/callback\"", "42"), MediaType.APPLICATION_JSON));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @Test
    void resolve_isOffWhenNoDomainIsTrusted() {

        var off = new HttpClientMetadataDocuments(RestClient.builder().build(), JsonMapper.builder().build(),
                new McpOAuthProperties(Set.of()), clock);

        assertThat(off.resolve(CLIENT_ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/html", "text/plain;charset=UTF-8", "application/octet-stream", "application/jsonp"})
    void resolve_refusesANonJsonContentType(String contentType) {

        server.expect(requestTo(CLIENT_ID)).andRespond(withSuccess(DOCUMENT, MediaType.parseMediaType(contentType)));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @Test
    void resolve_refusesADocumentWithoutAContentType() {

        server.expect(requestTo(CLIENT_ID)).andRespond(withSuccess().body(DOCUMENT));

        assertThat(sut.resolve(CLIENT_ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json;charset=UTF-8", "application/json; charset=utf-8",
            "application/client-metadata+json"})
    void resolve_acceptsEveryJsonContentType(String contentType) {

        server.expect(requestTo(CLIENT_ID)).andRespond(withSuccess(DOCUMENT, MediaType.parseMediaType(contentType)));

        assertThat(sut.resolve(CLIENT_ID)).isPresent();
    }

    // How long a document is held

    @ParameterizedTest
    @ValueSource(strings = {"max-age=600", "public, max-age=600", "MAX-AGE=600", "max-age=\"600\""})
    void resolve_holdsADocumentForTheMaxAgeItsResponseAsks(String cacheControl) {

        assertHeldFor(600, cacheControl);
    }

    @Test
    void resolve_holdsADocumentWhoseNoCacheNamesAFieldForItsMaxAge() {

        assertHeldFor(600, "no-cache=\"Set-Cookie\", max-age=600");
    }

    @Test
    void resolve_ignoresTheDirectivesOfASharedCache() {

        assertHeldFor(600, "private, max-age=600, s-maxage=30");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-store", "No-Cache", "max-age=0", "public, max-age=600, no-store",
            "no-cache, max-age=600"})
    void resolve_holdsADocumentItsServerSaysNotToCacheForOneMinute(String cacheControl) {

        assertHeldFor(60, cacheControl);
    }

    @Test
    void resolve_holdsAMaxAgeUnderAMinuteForOneMinute() {

        assertHeldFor(60, "max-age=10");
    }

    @Test
    void resolve_treatsARepeatedMaxAgeAsStale() {

        assertHeldFor(60, "max-age=600, max-age=600");
    }

    @Test
    void resolve_readsEveryCacheControlFieldLine() {

        assertHeldFor(60, "max-age=600", "max-age=3600");
    }

    @ParameterizedTest
    @ValueSource(strings = {"max-age", "max-age=abc", "max-age = 600", "max-age=600, \"unterminated"})
    void resolve_treatsAnUnreadableCacheControlAsStale(String cacheControl) {

        assertHeldFor(60, cacheControl);
    }

    @ParameterizedTest
    @ValueSource(strings = {"public", "no-cache=\"Set-Cookie\"", ""})
    void resolve_holdsADocumentWithoutMaxAgeForOneHour(String cacheControl) {

        assertHeldFor(3_600, cacheControl);
    }

    @Test
    void resolve_holdsADocumentServedWithoutCacheControlForOneHour() {

        assertHeldFor(3_600);
    }

    @ParameterizedTest
    @ValueSource(strings = {"max-age=999999", "max-age=99999999999999999999"})
    void resolve_capsTheLifetimeAtOneDay(String cacheControl) {

        assertHeldFor(86_400, cacheControl);
    }

    @Test
    void resolve_aRefetchAfterTheLifetimeStartsANewOne() {

        // given
        server.expect(times(2), requestTo(CLIENT_ID)).andRespond(document("max-age=600"));

        // when
        sut.resolve(CLIENT_ID);
        clock.advance(Duration.ofSeconds(601));
        var refetched = sut.resolve(CLIENT_ID);
        clock.advance(Duration.ofSeconds(599));
        var held = sut.resolve(CLIENT_ID);

        // then
        assertThat(refetched).isPresent();
        assertThat(held).isPresent();
        server.verify();
    }

    // What is never stored

    static Stream<Arguments> nonAnswers() {

        return Stream.of(arguments(named("an error status", withStatus(HttpStatus.SERVICE_UNAVAILABLE))),
                arguments(named("a timeout", withException(new SocketTimeoutException("read timed out")))),
                arguments(named("a body that is not JSON",
                        withSuccess("<html>not a metadata document</html>", MediaType.APPLICATION_JSON))),
                arguments(named("an invalid document", withSuccess(
                        DOCUMENT.replace("\"none\"", "\"client_secret_basic\""), MediaType.APPLICATION_JSON))));
    }

    @ParameterizedTest
    @MethodSource("nonAnswers")
    void resolve_neverStoresAFailureOrAnInvalidDocument(ResponseCreator nonAnswer) {

        // given
        server.expect(once(), requestTo(CLIENT_ID)).andRespond(nonAnswer);
        server.expect(once(), requestTo(CLIENT_ID)).andRespond(document());

        // when
        var refused = sut.resolve(CLIENT_ID);
        clock.advance(Duration.ofSeconds(10));
        var actual = sut.resolve(CLIENT_ID);

        // then
        assertThat(refused).isEmpty();
        assertThat(actual).isPresent();
        server.verify();
    }

    @Test
    void resolve_refusesALapsedDocumentWhoseRefetchFails() {

        // given
        server.expect(once(), requestTo(CLIENT_ID)).andRespond(document("max-age=600"));
        server.expect(once(), requestTo(CLIENT_ID)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(CLIENT_ID)).andRespond(document("max-age=600"));

        // when
        sut.resolve(CLIENT_ID);
        clock.advance(Duration.ofSeconds(601));
        var lapsed = sut.resolve(CLIENT_ID);
        var retried = sut.resolve(CLIENT_ID);

        // then
        assertThat(lapsed).isEmpty();
        assertThat(retried).isPresent();
        server.verify();
    }

    @Test
    void resolve_aFloodOfFailingClientIdsStoresNothing() {

        // given: the real client held, then 256 ids that fail, the first of them asked twice
        server.expect(once(), requestTo(CLIENT_ID)).andRespond(document());
        server.expect(times(257), requestTo(startsWith("https://claude.ai/spam-")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        sut.resolve(CLIENT_ID);
        for (var i = 0; i < 256; i++) {
            sut.resolve("https://claude.ai/spam-" + i);
        }

        // when
        var spam = sut.resolve("https://claude.ai/spam-0");
        var actual = sut.resolve(CLIENT_ID);

        // then
        assertThat(spam).isEmpty();
        assertThat(actual).isPresent();
        server.verify();
    }

    // Single flight

    @Test
    void resolve_concurrentResolutionsOfOneClientShareOneFetch() throws InterruptedException {

        // given
        var fetching = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.expect(once(), requestTo(CLIENT_ID)).andRespond(heldUntil(fetching, release, document("max-age=600")));

        // when
        var actual = resolveConcurrently(fetching, release);

        // then
        assertThat(actual).hasSize(CONCURRENT_CALLERS).allMatch(Optional::isPresent);
        server.verify();
    }

    @Test
    void resolve_concurrentResolutionsOfOneClientShareOneFailedFetch() throws InterruptedException {

        // given
        var fetching = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        server.expect(once(), requestTo(CLIENT_ID))
                .andRespond(heldUntil(fetching, release, withStatus(HttpStatus.SERVICE_UNAVAILABLE)));

        // when
        var actual = resolveConcurrently(fetching, release);

        // then
        assertThat(actual).hasSize(CONCURRENT_CALLERS).allMatch(Optional::isEmpty);
        server.verify();
    }

    /** Fetched at once, still held one second before {@code seconds} have passed, fetched again one second after. */
    private void assertHeldFor(long seconds, String... cacheControl) {

        // given
        var requests = new AtomicInteger();
        server.expect(times(2), requestTo(CLIENT_ID)).andRespond(request -> {
            requests.incrementAndGet();
            return document(cacheControl).createResponse(request);
        });

        // when
        var fetched = sut.resolve(CLIENT_ID);
        clock.advance(Duration.ofSeconds(seconds - 1));
        var held = sut.resolve(CLIENT_ID);
        var requestsWhileHeld = requests.get();
        clock.advance(Duration.ofSeconds(2));
        var refetched = sut.resolve(CLIENT_ID);

        // then
        assertThat(requestsWhileHeld).as("requests before the lifetime ends").isOne();
        assertThat(List.of(fetched, held, refetched)).allMatch(Optional::isPresent);
        server.verify();
    }

    /**
     * The first caller's fetch is held open until every other caller has either parked on the shared fetch or finished,
     * so a resolver without single flight sends its own requests while the first is still in flight, and the server
     * refuses them.
     */
    private List<Optional<ClientMetadata>> resolveConcurrently(CountDownLatch fetching, CountDownLatch release)
            throws InterruptedException {

        var results = new ConcurrentLinkedQueue<Optional<ClientMetadata>>();
        var failures = new ConcurrentLinkedQueue<Throwable>();
        Runnable resolve = () -> {
            try {
                results.add(sut.resolve(CLIENT_ID));
            } catch (Throwable e) {
                failures.add(e);
            }
        };
        var first = Thread.ofPlatform().start(resolve);
        assertThat(fetching.await(10, TimeUnit.SECONDS)).as("the first fetch started").isTrue();
        var others = new ArrayList<Thread>();
        for (var i = 1; i < CONCURRENT_CALLERS; i++) {
            others.add(Thread.ofPlatform().start(resolve));
        }
        await("every other caller parked on the shared CompletableFuture or done").atMost(Duration.ofSeconds(10))
                .until(() -> others.stream().allMatch(HttpClientMetadataDocumentsTest::isParkedOnTheSharedFetchOrDone));
        release.countDown();
        assertThat(first.join(Duration.ofSeconds(10))).isTrue();
        for (var thread : others) {
            assertThat(thread.join(Duration.ofSeconds(10))).isTrue();
        }
        assertThat(failures).isEmpty();
        return List.copyOf(results);
    }

    /**
     * Done, or parked on the shared future itself: a thread merely starting up can be briefly waiting or blocked before
     * it reaches the cache, and releasing the fetch then would let it start a second one.
     */
    private static boolean isParkedOnTheSharedFetchOrDone(Thread thread) {

        return thread.getState() == Thread.State.TERMINATED
                || thread.getState() == Thread.State.WAITING && Arrays.stream(thread.getStackTrace())
                        .anyMatch(frame -> frame.getClassName().equals(CompletableFuture.class.getName()));
    }

    private static ResponseCreator heldUntil(CountDownLatch fetching, CountDownLatch release,
            ResponseCreator response) {

        return request -> {
            fetching.countDown();
            try {
                assertThat(release.await(10, TimeUnit.SECONDS)).as("the test released the fetch").isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return response.createResponse(request);
        };
    }

    private static DefaultResponseCreator document(String... cacheControl) {

        return withSuccess(DOCUMENT, MediaType.APPLICATION_JSON).header(HttpHeaders.CACHE_CONTROL, cacheControl);
    }

    private static String documentOfSize(int bytes) {

        var unpadded = DOCUMENT.replace("\"response_types\"", "\"pad\":\"\",\"response_types\"");
        var padding = "x".repeat(bytes - unpadded.getBytes(UTF_8).length);
        return unpadded.replace("\"pad\":\"\"", "\"pad\":\"" + padding + "\"");
    }
}
