package dev.axonia.selfhosted.configuration;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;

import com.nimbusds.jose.KeySourceException;

import dev.axonia.SteppableClock;
import dev.axonia.selfhosted.LocalIssuer;
import dev.axonia.selfhosted.adapter.in.security.IssuerKeys;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The installation's decoder against an issuer over HTTP whose key endpoint stops serving keys after the first fetch.
 * Each outcome is read as core's bearer manager answers it: 401 for a rejected token, 503 for an issuer it cannot have
 * the token's key from.
 */
@ExtendWith(OutputCaptureExtension.class)
class IssuerOutageTest {

    private static final int ACCEPTED = 200;

    private static final int REJECTED = 401;

    private static final int UNAVAILABLE = 503;

    private static final int FAILED = 500;

    private final SteppableClock clock = new SteppableClock(Instant.now());

    private final LocalIssuer issuer = LocalIssuer.start();

    private final SelfHostedSecurityConfig config = new SelfHostedSecurityConfig();

    private final IssuerKeys keys = config.issuerKeys(properties(issuer.uri()), clock);

    private final JwtDecoder decoder = config.jwtDecoder(keys, properties(issuer.uri()), clock);

    @AfterEach
    void stop() throws IOException {

        keys.close();
        issuer.close();
    }

    @ParameterizedTest
    @EnumSource(value = LocalIssuer.Keys.class, names = {"THROTTLED", "MALFORMED"})
    void aKeyEndpointThatAnswersWithoutKeysNeverRefusesATokenItsCachedKeysVerify(LocalIssuer.Keys answer) {

        // given - signed in while the issuer served its keys, which then throttles or garbles every later fetch
        var signedIn = issuer.token(issuer.first);
        assertThat(answerTo(signedIn)).isEqualTo(ACCEPTED);
        issuer.keys = answer;
        clock.advance(Duration.ofSeconds(10));

        // when - anyone sends tokens naming keys nobody published
        var forged = IntStream.range(0, 5).mapToObj(i -> answerTo(issuer.forged())).toList();

        // then
        assertThat(forged).containsOnly(REJECTED);
        assertThat(answerTo(signedIn)).isEqualTo(ACCEPTED);
        assertThat(issuer.keyFetches).hasValue(2);
    }

    @Test
    void aFailingKeyEndpointNeverRefusesATokenItsCachedKeysVerify() {

        // given
        var signedIn = issuer.token(issuer.first);
        assertThat(answerTo(signedIn)).isEqualTo(ACCEPTED);
        issuer.keys = LocalIssuer.Keys.FAILING;
        clock.advance(Duration.ofSeconds(10));

        // when
        var forged = List.of(answerTo(issuer.forged()), answerTo(issuer.forged()));

        // then - a key it does not hold cannot be had while the issuer fails, the keys it holds still verify
        assertThat(forged).containsOnly(UNAVAILABLE);
        assertThat(answerTo(signedIn)).isEqualTo(ACCEPTED);
        assertThat(issuer.keyFetches).hasValue(2);
    }

    @Test
    void anIssuerThatStopsAnsweringIsAskedOnceAndTheTokensWaitingOnItAreAnsweredWithinTheCallsDeadline(
            CapturedOutput output) throws Exception {

        // given - a key rotated in while the issuer stopped answering
        var signedIn = issuer.token(issuer.first);
        assertThat(answerTo(signedIn)).isEqualTo(ACCEPTED);
        var rotated = issuer.token(issuer.rotate());
        issuer.keys = LocalIssuer.Keys.HANGING;
        clock.advance(Duration.ofSeconds(10));

        // when - five tokens signed with it arrive together, then four more once the first fetch failed
        var together = timed(5, rotated);
        var after = timed(4, rotated);

        // then - the one that called waits for the call's ten-second deadline, the others five seconds at most
        assertThat(together).allSatisfy(answer -> {
            assertThat(answer.status()).isEqualTo(UNAVAILABLE);
            assertThat(answer.took()).isLessThan(Duration.ofSeconds(11));
        });
        assertThat(together).filteredOn(answer -> answer.took().compareTo(Duration.ofSeconds(6)) < 0).hasSize(4);
        assertThat(after).allSatisfy(answer -> {
            assertThat(answer.status()).isEqualTo(UNAVAILABLE);
            assertThat(answer.took()).isLessThan(Duration.ofMillis(500));
        });
        assertThat(answerTo(signedIn)).isEqualTo(ACCEPTED);
        assertThat(issuer.keyFetches).hasValue(2);
        assertThat(output.getOut().split("The identity provider's keys could not be fetched", -1)).hasSize(2);
    }

    @Test
    void anIssuerThatAnswersAgainIsAskedOnceTheIntervalEndsAndItsAnswerReplacesTheOutage() {

        // given - the issuer failed the fetch a rotated key needed, then recovered
        assertThat(answerTo(issuer.token(issuer.first))).isEqualTo(ACCEPTED);
        var rotated = issuer.token(issuer.rotate());
        issuer.keys = LocalIssuer.Keys.FAILING;
        clock.advance(Duration.ofSeconds(10));
        assertThat(answerTo(rotated)).isEqualTo(UNAVAILABLE);
        issuer.keys = LocalIssuer.Keys.SERVED;
        assertThat(answerTo(rotated)).isEqualTo(UNAVAILABLE);

        // when
        clock.advance(Duration.ofSeconds(10));
        var recovered = answerTo(rotated);
        var forged = answerTo(issuer.forged());

        // then - a key nobody published is unknown again, no longer a key that cannot be had
        assertThat(recovered).isEqualTo(ACCEPTED);
        assertThat(forged).isEqualTo(REJECTED);
        assertThat(issuer.keyFetches).hasValue(3);
    }

    /** What core's bearer manager answers for the decoder's outcome; {@code SecurityConfig} holds that mapping. */
    private int answerTo(String token) {

        try {
            decoder.decode(token);
            return ACCEPTED;
        } catch (BadJwtException e) {
            return REJECTED;
        } catch (JwtDecoderInitializationException e) {
            return UNAVAILABLE;
        } catch (JwtException e) {
            return keySourceFailure(e) ? UNAVAILABLE : FAILED;
        }
    }

    private static boolean keySourceFailure(Throwable failure) {

        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof KeySourceException) {
                return true;
            }
        }
        return false;
    }

    /** Sends the token from that many threads at once, each timing its own answer. */
    private List<Answer> timed(int threads, String token) throws Exception {

        var requests = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var answers = IntStream.range(0, threads)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> timedAnswerTo(token), requests)).toList();
            CompletableFuture.allOf(answers.toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);
            return answers.stream().map(CompletableFuture::join).toList();
        } finally {
            requests.shutdownNow();
        }
    }

    private Answer timedAnswerTo(String token) {

        var started = System.nanoTime();
        var status = answerTo(token);
        return new Answer(status, Duration.ofNanos(System.nanoTime() - started));
    }

    private static SelfHostedProperties properties(String issuer) {

        return new SelfHostedProperties(new SelfHostedProperties.Organization("acme", "Acme"), "owners",
                new SelfHostedProperties.Oidc(issuer, LocalIssuer.AUDIENCE, "sub", "groups", List.of("email"), "name"));
    }

    private record Answer(int status, Duration took) {
    }
}
