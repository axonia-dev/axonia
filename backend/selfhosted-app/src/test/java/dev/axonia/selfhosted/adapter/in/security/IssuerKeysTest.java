package dev.axonia.selfhosted.adapter.in.security;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;

import dev.axonia.SteppableClock;
import dev.axonia.selfhosted.LocalIssuer;
import dev.axonia.selfhosted.configuration.SelfHostedSecurityConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.awaitility.Awaitility.await;

@ExtendWith(OutputCaptureExtension.class)
class IssuerKeysTest {

    private static final String FORGED = "forged";

    private final SteppableClock clock = new SteppableClock(Instant.parse("2026-10-03T09:00:00Z"));

    private final LocalIssuer issuer = LocalIssuer.start();

    private IssuerKeys keys = keysOf(issuer.uri());

    @AfterEach
    void stop() throws IOException {

        keys.close();
        issuer.close();
    }

    @Test
    void theIssuerIsAskedForItsDiscoveryDocumentOnceAndForItsKeysWhenAKeyIsNeeded() throws KeySourceException {

        // given
        assertThat(issuer.discoveryFetches).hasValue(0);
        assertThat(issuer.keyFetches).hasValue(0);

        // when
        var actual = held("first");
        clock.advance(Duration.ofSeconds(10));
        held(FORGED);

        // then
        assertThat(actual).extracting(JWK::getKeyID).containsExactly("first");
        assertThat(issuer.discoveryFetches).hasValue(1);
        assertThat(issuer.keyFetches).hasValue(2);
    }

    @Test
    void aKeyNobodyPublishedIsUnknownWhileTheIssuerAnswers() throws KeySourceException {

        // given
        held("first");

        // when
        var asked = held(FORGED);
        clock.advance(Duration.ofSeconds(10));
        var askedAgain = held(FORGED);

        // then
        assertThat(asked).isEmpty();
        assertThat(askedAgain).isEmpty();
        assertThat(issuer.keyFetches).hasValue(2);
    }

    @Test
    void theIssuerIsAskedForItsKeysAtMostOnceEveryTenSeconds() throws KeySourceException {

        // given
        held("first");

        // when
        var fetches = new ArrayList<Integer>();
        for (var step : List.of(Duration.ZERO, Duration.ofSeconds(9), Duration.ofSeconds(1), Duration.ofSeconds(9))) {
            clock.advance(step);
            held(FORGED);
            fetches.add(issuer.keyFetches.get());
        }

        // then - asked again at ten seconds, not at nine nor at nineteen
        assertThat(fetches).containsExactly(1, 1, 2, 2);
    }

    @Test
    void aKeyNotHeldCannotBeHadWhileTheIssuerFails() throws KeySourceException {

        // given
        held("first");
        issuer.keys = LocalIssuer.Keys.FAILING;
        clock.advance(Duration.ofSeconds(10));

        // when / then
        assertThatThrownBy(() -> held(FORGED)).isInstanceOf(IssuerKeys.Unavailable.class);
        assertThatThrownBy(() -> held(FORGED)).isInstanceOf(IssuerKeys.Unavailable.class);
        assertThat(held("first")).extracting(JWK::getKeyID).containsExactly("first");
        assertThat(issuer.keyFetches).hasValue(2);
    }

    @Test
    void keysHeldOutliveAFailingIssuerByTheOutageToleranceAndNoLonger() throws Exception {

        // given - keys held 400 ms and kept 1.5 s after the last fetch that worked
        keys.close();
        var fetched = System.nanoTime();
        keys = new IssuerKeys(issuer.uri(), new RestTemplate(SelfHostedSecurityConfig.issuerRequests()), clock,
                new IssuerKeys.Lifetimes(Duration.ofMillis(400), Duration.ofMillis(200), Duration.ofMillis(100),
                        Duration.ofMillis(1500)));
        held("first");
        issuer.keys = LocalIssuer.Keys.FAILING;
        clock.advance(Duration.ofSeconds(10));

        // when - the key held is asked for until it can no longer be had
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(20)).until(() -> !holds("first"));
        var outlived = Duration.ofNanos(System.nanoTime() - fetched);

        // then
        assertThat(outlived).isGreaterThan(Duration.ofMillis(1400));
        assertThat(issuer.keyFetches).hasValue(2);
    }

    @Test
    void withNoKeyEverFetchedEveryKeyIsUnavailableWhateverTheIssuerAnswered() throws KeySourceException {

        // given
        issuer.keys = LocalIssuer.Keys.THROTTLED;

        // when / then
        assertThatThrownBy(() -> held("first")).isInstanceOf(IssuerKeys.Unavailable.class);
        assertThatThrownBy(() -> held("first")).isInstanceOf(IssuerKeys.Unavailable.class);
        issuer.keys = LocalIssuer.Keys.SERVED;
        clock.advance(Duration.ofSeconds(10));
        assertThat(held("first")).extracting(JWK::getKeyID).containsExactly("first");
        assertThat(issuer.keyFetches).hasValue(2);
    }

    @Test
    void anIssuerThatCannotBeReachedIsAskedAgainOnceTheIntervalEnds(CapturedOutput output) throws IOException {

        // given
        keys.close();
        keys = keysOf("http://127.0.0.1:9");

        // when
        var first = catchUnavailable();
        var withinTheInterval = catchUnavailable();
        clock.advance(Duration.ofSeconds(10));
        var afterIt = catchUnavailable();

        // then
        assertThat(first).hasRootCauseInstanceOf(ConnectException.class);
        assertThat(withinTheInterval).hasRootCauseMessage("The issuer's keys were fetched less than 10 s ago");
        assertThat(afterIt).hasRootCauseInstanceOf(ConnectException.class);
        assertThat(output.getOut().split("The identity provider's keys could not be fetched", -1)).hasSize(3);
        assertThat(output.getOut()).contains("java.net.ConnectException").doesNotContain("\tat ");
    }

    @Test
    void aDiscoveryDocumentOfAnotherIssuerGivesNoKeys() {

        // given
        issuer.discovers("{\"issuer\":\"https://elsewhere.example\",\"jwks_uri\":\"" + issuer.uri() + "/keys\"}");

        // when
        var actual = catchUnavailable();

        // then
        assertThat(actual).hasRootCauseMessage(
                "The discovery document names the issuer https://elsewhere.example, not " + issuer.uri());
        assertThat(issuer.keyFetches).hasValue(0);
    }

    @Test
    void aDiscoveryDocumentNamingNoKeysGivesNoKeys() {

        // given
        issuer.discovers("{\"issuer\":\"" + issuer.uri() + "\"}");

        // when
        var actual = catchUnavailable();

        // then
        assertThat(actual).hasRootCauseMessage("The discovery document of " + issuer.uri() + " names no jwks_uri");
    }

    @Test
    void keysAskedForWhileTheFirstFetchIsUnderWayWaitForIt() throws Exception {

        // given
        issuer.keys = LocalIssuer.Keys.SLOW;
        var requests = Executors.newVirtualThreadPerTaskExecutor();

        try {
            // when
            var asked = IntStream.range(0, 4)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> heldUnchecked("first"), requests)).toList();
            CompletableFuture.allOf(asked.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

            // then
            assertThat(asked).map(CompletableFuture::join)
                    .allSatisfy(actual -> assertThat(actual).extracting(JWK::getKeyID).containsExactly("first"));
            assertThat(issuer.keyFetches).hasValue(1);
        } finally {
            requests.shutdownNow();
        }
    }

    @Test
    void keysAskedForWhileASlowFetchIsUnderWayAreRefusedAfterFiveSeconds() throws Exception {

        // given - a fetch the issuer keeps alive for seven seconds
        issuer.keys = LocalIssuer.Keys.TRICKLING;
        var requests = Executors.newVirtualThreadPerTaskExecutor();

        try {
            var fetching = CompletableFuture.supplyAsync(() -> heldUnchecked("first"), requests);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(issuer.keyFetches).hasValue(1));

            // when
            var waiting = IntStream.range(0, 3).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                var started = System.nanoTime();
                assertThatThrownBy(() -> held("first")).isInstanceOf(IssuerKeys.Unavailable.class);
                return Duration.ofNanos(System.nanoTime() - started);
            }, requests)).toList();
            CompletableFuture.allOf(waiting.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);

            // then
            assertThat(waiting).map(CompletableFuture::join)
                    .allSatisfy(waited -> assertThat(waited).isLessThan(Duration.ofSeconds(6)));
            assertThat(fetching.get(10, TimeUnit.SECONDS)).extracting(JWK::getKeyID).containsExactly("first");
        } finally {
            requests.shutdownNow();
        }
    }

    @ParameterizedTest
    @EnumSource(value = LocalIssuer.Keys.class, names = {"HANGING", "TRICKLING"})
    void aKeyHeldNeverWaitsOnARefreshTheIssuerDoesNotAnswer(LocalIssuer.Keys answer) throws Exception {

        // given - keys held for five seconds and refreshed after one, from an issuer whose every call is cut after one
        keys.close();
        keys = keysCutAfterASecond(new IssuerKeys.Lifetimes(Duration.ofSeconds(5), Duration.ofSeconds(3),
                Duration.ofSeconds(1), Duration.ofMinutes(15)));
        held("first");
        issuer.keys = answer;
        clock.advance(Duration.ofSeconds(10));

        // when - the key held is asked for every 100 ms until well past its first lapse
        var waits = new ArrayList<Duration>();
        var until = System.nanoTime() + Duration.ofSeconds(7).toNanos();
        while (System.nanoTime() < until) {
            var started = System.nanoTime();
            assertThat(held("first")).extracting(JWK::getKeyID).containsExactly("first");
            waits.add(Duration.ofNanos(System.nanoTime() - started));
            Thread.sleep(Duration.ofMillis(100));
        }

        // then - a refresh reached the issuer, and no request waited on it
        assertThat(issuer.keyFetches).hasValue(2);
        assertThat(waits).allSatisfy(waited -> assertThat(waited).isLessThan(Duration.ofMillis(500)));
    }

    @Test
    void aKeyHeldNeverWaitsAfterAQuietSpellOnAnIssuerThatDoesNotAnswer() throws Exception {

        // given - keys held for five seconds and refreshed after one, nobody asking for them past their first lapse
        keys.close();
        keys = keysCutAfterASecond(new IssuerKeys.Lifetimes(Duration.ofSeconds(5), Duration.ofSeconds(3),
                Duration.ofSeconds(1), Duration.ofMinutes(15)));
        held("first");
        issuer.keys = LocalIssuer.Keys.HANGING;
        clock.advance(Duration.ofSeconds(10));
        Thread.sleep(Duration.ofMillis(6500));

        // when
        var started = System.nanoTime();
        var actual = held("first");
        var waited = Duration.ofNanos(System.nanoTime() - started);

        // then
        assertThat(actual).extracting(JWK::getKeyID).containsExactly("first");
        assertThat(waited).isLessThan(Duration.ofMillis(500));
        assertThat(issuer.keyFetches).hasValue(2);
    }

    @Test
    void closingTheKeysStopsTheirRefreshes() throws Exception {

        // given
        held("first");

        // when
        keys.close();

        // then
        assertThat(keys).extracting("held.JWKSetSource").satisfies(source -> {
            assertThat(source).extracting("executorService", type(ExecutorService.class)).returns(true,
                    ExecutorService::isShutdown);
            assertThat(source).extracting("scheduledExecutorService", type(ExecutorService.class)).returns(true,
                    ExecutorService::isShutdown);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"executorService", "scheduledExecutorService"})
    void theKeysAreRefreshedOnDaemonThreads(String refreshes) throws Exception {

        // given
        var executor = assertThat(keys).extracting("held.JWKSetSource." + refreshes, type(ExecutorService.class))
                .actual();

        // when
        var actual = executor.submit(() -> Thread.currentThread().isDaemon()).get(5, TimeUnit.SECONDS);

        // then - a refresh never keeps the JVM alive
        assertThat(actual).isTrue();
    }

    @Test
    void aRefreshCutShortByClosingTheKeysSaysNothingOfTheIssuer(CapturedOutput output) throws Exception {

        // given - keys held 1.5 s and refreshed after 0.5 s, from an issuer that stops answering
        keys.close();
        keys = new IssuerKeys(issuer.uri(), new RestTemplate(SelfHostedSecurityConfig.issuerRequests()), clock,
                new IssuerKeys.Lifetimes(Duration.ofMillis(1500), Duration.ofMillis(500), Duration.ofMillis(500),
                        Duration.ofMinutes(15)));
        held("first");
        issuer.keys = LocalIssuer.Keys.HANGING;
        clock.advance(Duration.ofSeconds(10));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(issuer.keyFetches).hasValue(2));
        var refreshing = assertThat(keys).extracting("held.JWKSetSource.executorService", type(ExecutorService.class))
                .actual();

        // when
        keys.close();

        // then - once the interrupted refresh has ended
        assertThat(refreshing.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(output.getOut()).doesNotContain("could not be fetched");
        assertThat(keys).extracting("fetch.unreachable").isEqualTo(false);
    }

    @Test
    void aFetchInterruptedOnTheThreadThatAskedLeavesItInterruptedAndTheKeyUnknown() throws Exception {

        // given - a key held, and an issuer that stops answering
        held("first");
        clock.advance(Duration.ofSeconds(10));
        issuer.keys = LocalIssuer.Keys.HANGING;
        var actual = new CompletableFuture<Map.Entry<List<JWK>, Boolean>>();
        var asking = Thread.ofPlatform().start(() -> {
            try {
                actual.complete(Map.entry(held(FORGED), Thread.currentThread().isInterrupted()));
            } catch (KeySourceException e) {
                actual.completeExceptionally(e);
            }
        });
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(issuer.keyFetches).hasValue(2));

        // when
        asking.interrupt();

        // then - the key is unknown, the thread still interrupted, and nothing said of the issuer
        assertThat(actual.get(5, TimeUnit.SECONDS)).satisfies(answer -> {
            assertThat(answer.getKey()).isEmpty();
            assertThat(answer.getValue()).isTrue();
        });
        assertThat(keys).extracting("fetch.unreachable").isEqualTo(false);
    }

    @Test
    void aKeyNotHeldCannotBeHadWhileTheIssuerTricklesItsAnswer() throws Exception {

        // given
        keys.close();
        keys = keysCutAfterASecond(IssuerKeys.Lifetimes.STANDARD);
        held("first");
        issuer.rotate();
        issuer.keys = LocalIssuer.Keys.TRICKLING;
        clock.advance(Duration.ofSeconds(10));

        // when / then - the call cut while the answer arrives means the issuer cannot be reached
        assertThatThrownBy(() -> held("rotated")).isInstanceOf(IssuerKeys.Unavailable.class);
        assertThat(held("first")).extracting(JWK::getKeyID).containsExactly("first");
    }

    @Test
    void keysNamedOverAnotherSchemeThanTheIssuersAreNeverAskedFor() {

        // given
        var elsewhere = issuer.uri().replace("http:", "https:") + "/keys";
        issuer.discovers("{\"issuer\":\"" + issuer.uri() + "\",\"jwks_uri\":\"" + elsewhere + "\"}");

        // when
        var actual = catchUnavailable();

        // then
        assertThat(actual).hasRootCauseMessage(
                "The discovery document of " + issuer.uri() + " names keys over another scheme: " + elsewhere);
        assertThat(issuer.keyFetches).hasValue(0);
    }

    @ParameterizedTest
    @EnumSource(value = LocalIssuer.Keys.class, names = {"MISSING", "GONE"})
    void keysMovedElsewhereAreFoundThroughTheDiscoveryDocumentAgain(LocalIssuer.Keys answer) throws Exception {

        // given - the keys held, then moved, and their old address answers that they are not there
        held("first");
        issuer.rotate();
        issuer.keys = answer;
        issuer.discovers("{\"issuer\":\"" + issuer.uri() + "\",\"jwks_uri\":\"" + issuer.uri() + "/moved-keys\"}");
        clock.advance(Duration.ofSeconds(10));

        // when
        var atTheOldAddress = held("rotated");
        var heldMeanwhile = held("first");
        clock.advance(Duration.ofSeconds(10));
        var actual = held("rotated");

        // then - the old address answered, so the key was unknown rather than unavailable until the move was read
        assertThat(atTheOldAddress).isEmpty();
        assertThat(heldMeanwhile).extracting(JWK::getKeyID).containsExactly("first");
        assertThat(actual).extracting(JWK::getKeyID).containsExactly("rotated");
        assertThat(issuer.discoveryFetches).hasValue(2);
        assertThat(issuer.movedKeyFetches).hasValue(1);
    }

    @ParameterizedTest
    @EnumSource(value = LocalIssuer.Keys.class, names = {"FAILING", "THROTTLED", "MALFORMED"})
    void aKeyEndpointThatFailsOtherwiseIsNotDiscoveredAgain(LocalIssuer.Keys answer) throws Exception {

        // given
        held("first");
        issuer.keys = answer;

        // when
        for (var attempt = 0; attempt < 2; attempt++) {
            clock.advance(Duration.ofSeconds(10));
            catchThrowable(() -> held(FORGED));
        }

        // then
        assertThat(issuer.keyFetches).hasValue(3);
        assertThat(issuer.discoveryFetches).hasValue(1);
    }

    @Test
    void anIssuerEndingInASlashIsDiscoveredBelowIt() throws Exception {

        // given - an issuer named the way Auth0 names its own
        var withASlash = issuer.uri() + "/";
        issuer.discovers("{\"issuer\":\"" + withASlash + "\",\"jwks_uri\":\"" + issuer.uri() + "/keys\"}");
        keys.close();
        keys = keysOf(withASlash);

        // when
        var actual = held("first");

        // then
        assertThat(actual).extracting(JWK::getKeyID).containsExactly("first");
        assertThat(issuer.discoveryFetches).hasValue(1);
    }

    private IssuerKeys keysCutAfterASecond(IssuerKeys.Lifetimes lifetimes) {

        var requests = new JdkClientHttpRequestFactory();
        requests.setReadTimeout(Duration.ofSeconds(1));
        return new IssuerKeys(issuer.uri(), new RestTemplate(requests), clock, lifetimes);
    }

    private IssuerKeys keysOf(String uri) {

        return new IssuerKeys(uri, new RestTemplate(SelfHostedSecurityConfig.issuerRequests()), clock,
                IssuerKeys.Lifetimes.STANDARD);
    }

    private boolean holds(String keyId) throws KeySourceException {

        try {
            assertThat(held(keyId)).extracting(JWK::getKeyID).containsExactly(keyId);
            return true;
        } catch (IssuerKeys.Unavailable e) {
            return false;
        }
    }

    private List<JWK> held(String keyId) throws KeySourceException {

        return keys.get(new JWKSelector(new JWKMatcher.Builder().keyID(keyId).build()), null);
    }

    private List<JWK> heldUnchecked(String keyId) {

        try {
            return held(keyId);
        } catch (KeySourceException e) {
            throw new IllegalStateException(e);
        }
    }

    private Throwable catchUnavailable() {

        try {
            held("first");
        } catch (KeySourceException e) {
            assertThat(e).isInstanceOf(IssuerKeys.Unavailable.class);
            return e;
        }
        throw new AssertionError("the key was had");
    }
}
