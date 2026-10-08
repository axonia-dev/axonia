package dev.axonia;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

@ExtendWith(OutputCaptureExtension.class)
class RateLimiterTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final FakeRateLimitStore store = new FakeRateLimitStore();

    @Test
    void allowsUpToTheLimitThenRefuses() {

        var sut = rateLimiter("test", 3, Duration.ofMinutes(1));

        assertThat(sut.tryConsume("a").allowed()).isTrue();
        assertThat(sut.tryConsume("a").allowed()).isTrue();
        assertThat(sut.tryConsume("a").allowed()).isTrue();
        assertThat(sut.tryConsume("a").allowed()).isFalse();
    }

    @Test
    void keysDoNotShareABudget() {

        var sut = rateLimiter("test", 1, Duration.ofMinutes(1));

        assertThat(sut.tryConsume("a").allowed()).isTrue();
        assertThat(sut.tryConsume("a").allowed()).isFalse();
        assertThat(sut.tryConsume("b").allowed()).isTrue();
    }

    @Test
    void namespacesEveryKeyByItsLimiter() {

        // The bug this prevents: under the in-memory limiter each one owned a private map, so one address was a
        // different bucket in each. In one shared Redis keyspace an un-namespaced key would let an MCP auth-failure
        // flood spend that address's handoff-redeem budget, which is exactly the cross-contamination a shared keyspace
        // must not reintroduce.
        var authFailures = rateLimiter("mcp-auth-failure", 1, Duration.ofMinutes(1));
        var redeem = rateLimiter("handoff-redeem", 1, Duration.ofMinutes(1));

        assertThat(authFailures.tryConsume("203.0.113.7").allowed()).isTrue();

        assertThat(redeem.tryConsume("203.0.113.7").allowed()).isTrue();
        assertThat(store.keys()).containsExactlyInAnyOrder("ratelimit:mcp-auth-failure:203.0.113.7",
                "ratelimit:handoff-redeem:203.0.113.7");
    }

    @Test
    void refusesALimiterNameThatWouldBreakTheKeyspace() {

        // The prefix is colon-delimited, so a name carrying a colon makes ratelimit:<name>:<key> ambiguous. Braces are
        // worse still - they are Redis Cluster hash-tag syntax - but a colon is the one someone would actually type,
        // so it is the one that fails loudly.
        assertThatThrownBy(() -> rateLimiter("mcp:calls", 1, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("colon");
    }

    @Test
    void refusesABudgetThatCouldNeverAllowAnything() {

        assertThatThrownBy(() -> rateLimiter("test", 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("refuse every caller");
    }

    @Test
    void reportsHowLongUntilTheNextTokenRefills() {

        var sut = rateLimiter("test", 1, Duration.ofMinutes(1));
        sut.tryConsume("a");

        var refused = sut.tryConsume("a");

        assertThat(refused.allowed()).isFalse();
        assertThat(refused.retryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void countsEveryDecisionByOutcome() {

        var sut = rateLimiter("redeem", 3, Duration.ofMinutes(1));

        sut.tryConsume("a");
        sut.tryConsume("a");
        sut.tryConsume("a");
        sut.tryConsume("a");

        assertThat(count("redeem", "allowed")).isEqualTo(3);
        assertThat(count("redeem", "refused")).isEqualTo(1);
    }

    @Test
    void countsAndWarnsWhenTheStoreCouldNotBeReached(CapturedOutput output) {

        store.degrade();
        var sut = rateLimiter("test", 1, Duration.ofMinutes(1));

        // Fail open: the caller proceeds. But the decision was made without the shared state, so it is counted apart
        // from a real allow and it says so, because a guard rail that quietly stopped guarding is the worst case.
        assertThat(sut.tryConsume("a").allowed()).isTrue();
        assertThat(count("test", "degraded")).isEqualTo(1);
        assertThat(count("test", "allowed")).isZero();
        assertThat(output.getOut()).contains("Rate limit not enforced").contains("limiter=test");
    }

    @Test
    void warnsOnceForTheFirstRefusalNamingTheLimiterAndTheKey(CapturedOutput output) {

        var sut = rateLimiter("handoff-redeem", 1, Duration.ofMinutes(1));

        sut.tryConsume("203.0.113.7");
        sut.tryConsume("203.0.113.7");

        assertThat(warnings(output)).singleElement(as(STRING)).contains("limiter=handoff-redeem")
                .contains("key=203.0.113.7");
    }

    @Test
    void warnsAgainForEverySubsequentRefusal(CapturedOutput output) {

        var sut = rateLimiter("test", 1, Duration.ofMinutes(1));

        sut.tryConsume("a");
        sut.tryConsume("a");
        sut.tryConsume("a");
        sut.tryConsume("b");
        sut.tryConsume("b");

        assertThat(warnings(output)).hasSize(3);
    }

    @Test
    void staysQuietWhileTheBudgetHolds(CapturedOutput output) {

        var sut = rateLimiter("test", 3, Duration.ofMinutes(1));

        sut.tryConsume("a");
        sut.tryConsume("a");
        sut.tryConsume("a");

        assertThat(warnings(output)).isEmpty();
    }

    /**
     * The quiet path, for a caller that answers a refusal by waiting and asking again rather than by turning somebody
     * away. Same arithmetic, same decision, same {@code retryAfter} - counted apart so {@code outcome=refused} keeps
     * meaning "a limit turned somebody away", and logged nowhere so the WARN keeps meaning something when it appears.
     */
    @Test
    void aPacedRefusalIsCountedApartAndSaidNowhere(CapturedOutput output) {

        var sut = rateLimiter("outbox-send", 1, Duration.ofSeconds(1));

        assertThat(sut.tryConsumePaced("global").allowed()).isTrue();
        var refusal = sut.tryConsumePaced("global");

        assertThat(refusal.allowed()).isFalse();
        assertThat(refusal.retryAfter()).isEqualTo(Duration.ofSeconds(1));
        assertThat(count("outbox-send", "paced")).isEqualTo(1);
        assertThat(count("outbox-send", "refused")).isZero();
        assertThat(count("outbox-send", "allowed")).isEqualTo(1);
        assertThat(warnings(output)).isEmpty();
    }

    /**
     * Shared state that cannot be reached is an anomaly however routine a refusal is, so the quiet path is quiet about
     * refusals only. A guard rail that has silently stopped guarding still says so.
     */
    @Test
    void aPacedCallStillSaysWhenTheGuardRailIsDown(CapturedOutput output) {

        store.degrade();
        var sut = rateLimiter("outbox-send", 1, Duration.ofSeconds(1));

        assertThat(sut.tryConsumePaced("global").allowed()).isTrue();

        assertThat(count("outbox-send", "degraded")).isEqualTo(1);
        assertThat(output.getOut()).contains("Rate limit not enforced").contains("limiter=outbox-send");
    }

    private static List<String> warnings(CapturedOutput output) {

        return output.getOut().lines().filter(line -> line.contains("Rate limit exhausted")).toList();
    }

    private double count(String limiter, String outcome) {

        return meters.get("axonia.rate_limit").tag("limiter", limiter).tag("outcome", outcome).counter().count();
    }

    private RateLimiter rateLimiter(String name, int limit, Duration window) {

        return new RateLimiter(name, new Budget(limit, window), store, meters);
    }
}
