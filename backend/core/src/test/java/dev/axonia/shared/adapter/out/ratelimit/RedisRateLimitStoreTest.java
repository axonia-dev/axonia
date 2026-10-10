package dev.axonia.shared.adapter.out.ratelimit;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.axonia.SteppableClock;
import dev.axonia.shared.api.ratelimit.RateLimitAttempt.Outcome;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One of two classes in the suite that need Docker, alongside {@code RedisSessionDevicesTest}. One static container,
 * never per-method: this suite has a history of exhausting shared Testcontainers resources when contexts multiply.
 */
@Testcontainers
class RedisRateLimitStoreTest {

    private static final Instant START = Instant.parse("2026-07-30T10:00:00Z");
    private static final Duration MINUTE = Duration.ofMinutes(1);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory factory;

    private SteppableClock clock;
    private StringRedisTemplate redis;

    @BeforeEach
    void connect() {

        if (factory == null) {
            factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
            factory.afterPropertiesSet();
        }
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        clock = new SteppableClock(START);
    }

    @Test
    void allowsUpToCapacityThenRefuses() {

        var sut = new RedisRateLimitStore(redis, clock);

        assertThat(sut.tryConsume("k", 3, MINUTE).outcome()).isEqualTo(Outcome.ALLOWED);
        assertThat(sut.tryConsume("k", 3, MINUTE).outcome()).isEqualTo(Outcome.ALLOWED);
        assertThat(sut.tryConsume("k", 3, MINUTE).outcome()).isEqualTo(Outcome.ALLOWED);
        assertThat(sut.tryConsume("k", 3, MINUTE).outcome()).isEqualTo(Outcome.REFUSED);
    }

    @Test
    void reportsExactlyHowLongUntilTheNextToken() {

        var sut = new RedisRateLimitStore(redis, clock);
        for (var i = 0; i < 10; i++) {
            sut.tryConsume("k", 10, MINUTE);
        }

        // A capacity-10 bucket over 60s accrues a token every 6s, and the clock has not advanced.
        assertThat(sut.tryConsume("k", 10, MINUTE).retryAfter()).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void keysDoNotShareABudget() {

        var sut = new RedisRateLimitStore(redis, clock);

        assertThat(sut.tryConsume("a", 1, MINUTE).outcome()).isEqualTo(Outcome.ALLOWED);
        assertThat(sut.tryConsume("a", 1, MINUTE).outcome()).isEqualTo(Outcome.REFUSED);
        assertThat(sut.tryConsume("b", 1, MINUTE).outcome()).isEqualTo(Outcome.ALLOWED);
    }

    @Test
    void refillsAsTimePasses() {

        var sut = new RedisRateLimitStore(redis, clock);
        sut.tryConsume("k", 1, MINUTE);

        clock.advance(Duration.ofMinutes(2));

        assertThat(sut.tryConsume("k", 1, MINUTE).outcome()).isEqualTo(Outcome.ALLOWED);
    }

    @Test
    void setsATtlSoIdleBucketsExpireOnTheirOwn() {

        var sut = new RedisRateLimitStore(redis, clock);
        sut.tryConsume("k", 3, MINUTE);

        // The keyspace bound is Redis's now, not MAX_TRACKED_KEYS'. A bucket nobody touches must delete itself, or
        // attacker-chosen address keys accumulate until maxmemory evicts them.
        assertThat(redis.getExpire("k")).isPositive();
    }

    @Test
    void aSecondStoreOverTheSameRedisSeesTheFirstOnesConsumption() {

        var first = new RedisRateLimitStore(redis, clock);
        var second = new RedisRateLimitStore(redis, clock);

        assertThat(first.tryConsume("k", 1, MINUTE).outcome()).isEqualTo(Outcome.ALLOWED);

        // The entire point of this change: two instances, one budget. Under the in-memory limiter this passed with
        // ALLOWED and every replica multiplied every limit.
        assertThat(second.tryConsume("k", 1, MINUTE).outcome()).isEqualTo(Outcome.REFUSED);
    }

    /**
     * Drives identical sequences through both stores on separate, identically stepped clocks and asserts each call
     * agrees exactly - outcome and {@code retryAfter} both - proving the class Javadoc's claim that the two stores make
     * the same decision from the same state.
     *
     * <p>
     * The boundary section uses {@code capacity=10}, {@code window=10h} rather than the illustrative {@code
     * capacity=1}, {@code window=1h} the class Javadoc states the disagreement in: the ratio - one token's worth of
     * time is the same 3,600,000 milliseconds either way - and so the rounding arithmetic, is identical, but at
     * {@code capacity=1} the token this test drives toward 1 IS the whole bucket, so Redis's own {@code PEXPIRE} sets a
     * real, wall-clock TTL of about one millisecond right when the next call needs that key to still exist - a race
     * this suite cannot win against a real Redis round trip. At {@code capacity=10} the same near-1-token state leaves
     * roughly nine tokens' worth of bucket still unfilled, so the TTL stays hours away throughout.
     */
    @Test
    void matchesTheInMemoryStoreOnEveryCallOfTheSameSequence() {

        var memoryClock = new SteppableClock(START);
        var redisStore = new RedisRateLimitStore(redis, clock);
        var memoryStore = new InMemoryRateLimitStore(memoryClock);

        for (var i = 0; i < 10; i++) {
            assertParity(redisStore, memoryStore, "boundary", 10, Duration.ofHours(10));
        }
        step(memoryClock, Duration.ofMillis(3599999));
        assertParity(redisStore, memoryStore, "boundary", 10, Duration.ofHours(10));
        step(memoryClock, Duration.ofMillis(1));
        assertParity(redisStore, memoryStore, "boundary", 10, Duration.ofHours(10));
        step(memoryClock, Duration.ofMillis(1));
        assertParity(redisStore, memoryStore, "boundary", 10, Duration.ofHours(10));

        assertParity(redisStore, memoryStore, "backward", 1, MINUTE);
        step(memoryClock, Duration.ofSeconds(-30));
        assertParity(redisStore, memoryStore, "backward", 1, MINUTE);

        for (var i = 0; i < 60; i++) {
            assertParity(redisStore, memoryStore, "c32w15m", 32, Duration.ofMinutes(15));
            step(memoryClock, Duration.ofSeconds(17));
        }

        for (var i = 0; i < 20; i++) {
            assertParity(redisStore, memoryStore, "c3w24h", 3, Duration.ofHours(24));
            step(memoryClock, Duration.ofHours(5));
        }
    }

    /**
     * {@link #clock} (Redis's) and {@code memoryClock} are two separate {@link SteppableClock} instances, stepped
     * together by hand rather than shared, since the two stores must never be able to see each other's state through
     * anything but the recorded decisions themselves.
     */
    private void step(SteppableClock memoryClock, Duration by) {

        clock.advance(by);
        memoryClock.advance(by);
    }

    private static void assertParity(RedisRateLimitStore redisStore, InMemoryRateLimitStore memoryStore, String key,
            int capacity, Duration window) {

        assertThat(redisStore.tryConsume(key, capacity, window))
                .as("key %s at capacity %d over %s", key, capacity, window)
                .isEqualTo(memoryStore.tryConsume(key, capacity, window));
    }
}
