package dev.axonia.shared.adapter.out.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import dev.axonia.SteppableClock;
import dev.axonia.shared.api.ratelimit.RateLimitAttempt.Outcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class RedisRateLimitStoreDegradationTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-30T10:00:00Z"), ZoneOffset.UTC);

    private final UnreachableRedis redis = new UnreachableRedis();

    @Test
    void allowsTheCallWhenRedisIsUnreachable() {

        var sut = new RedisRateLimitStore(redis, FIXED);

        // Fail open: these budgets are abuse backstops, and a Redis blip must not become a customer-visible outage.
        assertThat(sut.tryConsume("k", 1, MINUTE).outcome()).isEqualTo(Outcome.DEGRADED);
    }

    @Test
    void warnsOnEveryDegradedDecisionSoAnUnguardedSurfaceIsVisible(CapturedOutput output) {

        var sut = new RedisRateLimitStore(redis, FIXED);

        sut.tryConsume("k", 1, MINUTE);

        assertThat(output.getOut()).contains("Rate limit degraded").contains("key=k");
    }

    @Test
    void stopsDiallingRedisOnceTheBreakerOpens() {

        var sut = new RedisRateLimitStore(redis, FIXED);

        for (var i = 0; i < 10; i++) {
            assertThat(sut.tryConsume("k", 1, MINUTE).outcome()).isEqualTo(Outcome.DEGRADED);
        }

        // Without the breaker a hung Redis would cost spring.data.redis.timeout on every one of those ten calls.
        assertThat(redis.attempts).isEqualTo(RedisRateLimitStore.FAILURE_THRESHOLD);
    }

    @Test
    void dialsAgainOnceTheOpenWindowHasPassed() {

        var clock = new SteppableClock(Instant.parse("2026-07-30T10:00:00Z"));
        var sut = new RedisRateLimitStore(redis, clock);
        for (var i = 0; i < RedisRateLimitStore.FAILURE_THRESHOLD; i++) {
            sut.tryConsume("k", 1, MINUTE);
        }

        clock.advance(RedisRateLimitStore.OPEN_DURATION.plusSeconds(1));
        sut.tryConsume("k", 1, MINUTE);

        assertThat(redis.attempts).isEqualTo(RedisRateLimitStore.FAILURE_THRESHOLD + 1);
    }

    @Test
    void surfacesAnImpossibleReplyInsteadOfTreatingItAsAllowed() {

        // A null reply means the script ran and returned nothing, which the script cannot do. That is a bug in the
        // script rather than a dependency being unavailable, so it must escape rather than be swallowed as a
        // degradation - a swallowed one would read as "allowed" forever and hide the defect.
        var sut = new RedisRateLimitStore(new SilentRedis(), FIXED);

        assertThatThrownBy(() -> sut.tryConsume("k", 1, MINUTE)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("k");
    }

    /** A Redis that answers, but with nothing. Only the script itself could cause this. */
    private static final class SilentRedis extends StringRedisTemplate {

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            return null;
        }
    }

    /** A Redis that is never there, counting how often it was asked. */
    private static final class UnreachableRedis extends StringRedisTemplate {

        private int attempts;

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            attempts++;
            throw new RedisConnectionFailureException("no route to redis");
        }
    }
}
