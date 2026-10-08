package dev.axonia.config;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import dev.axonia.RateLimitAttempt;
import dev.axonia.RateLimitStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Token buckets held in Redis, so every instance spends from one budget.
 *
 * <p>
 * One round trip per decision, and the arithmetic runs inside Redis. Atomicity comes from Lua being executed
 * single-threaded rather than from a compare-and-swap retry loop, so contention on a hot key costs no extra round
 * trips. Refill is lazy: a bucket nobody touches does no work and, thanks to the {@code PEXPIRE}, stops existing.
 *
 * <p>
 * {@code now} is passed in from the application's {@link Clock} rather than read with {@code redis.call('TIME')}. That
 * is required, not stylistic: {@code TIME} is non-deterministic and unsafe to replicate, and passing the clock in is
 * what lets the tests step time.
 *
 * <p>
 * <b>Failure is open, fast, and loud.</b> These budgets are abuse backstops, not security controls: a single-use
 * handoff code of 32 random bytes needs no budget to defend it, and the MCP failure budget caps log spam. Refusing
 * traffic because Redis restarted would turn a dependency added for correctness into a new single point of failure. So
 * an unreachable Redis allows the call - but every such decision is counted as {@code degraded} and logged, because a
 * guard rail that has quietly stopped guarding is worse than one that is known to be down.
 *
 * <p>
 * The breaker is what makes fail-open also fail-fast. A refused connection fails in about a millisecond, but a
 * <i>hung</i> Redis costs the full {@code spring.data.redis.timeout} on every request. After {@link #FAILURE_THRESHOLD}
 * consecutive failures the store stops dialling for {@link #OPEN_DURATION}, so an outage costs that timeout once rather
 * than once per request.
 */
@Slf4j
class RedisRateLimitStore implements RateLimitStore {

    static final int FAILURE_THRESHOLD = 3;
    static final Duration OPEN_DURATION = Duration.ofSeconds(30);

    /**
     * Returns 0 when the token was spent, otherwise the milliseconds until one exists. A single integer rather than a
     * pair is unambiguous because the refusal branch takes {@code math.ceil} of a strictly positive quantity and so
     * always returns at least 1, and it lets the Java side avoid a raw {@code List} generic.
     */
    private static final String SCRIPT = """
            local capacity     = tonumber(ARGV[1])
            local windowMillis = tonumber(ARGV[2])
            local now          = tonumber(ARGV[3])

            local state  = redis.call('HMGET', KEYS[1], 'tokens', 'at')
            local tokens = tonumber(state[1])
            local at     = tonumber(state[2])
            if tokens == nil or at == nil then
              tokens = capacity
              at = now
            end

            tokens = math.min(capacity, tokens + math.max(0, now - at) * capacity / windowMillis)

            local retryAfterMillis = 0
            if tokens >= 1 then
              tokens = tokens - 1
            else
              retryAfterMillis = math.ceil((1 - tokens) * windowMillis / capacity)
            end

            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'at', tostring(now))
            redis.call('PEXPIRE', KEYS[1], math.max(1, math.ceil((capacity - tokens) * windowMillis / capacity)))
            return retryAfterMillis
            """;

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(SCRIPT, Long.class);
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();

    RedisRateLimitStore(StringRedisTemplate redis, Clock clock) {

        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public RateLimitAttempt tryConsume(String key, int capacity, Duration window) {

        var now = clock.millis();
        if (now < openUntil.get()) {
            return RateLimitAttempt.degraded();
        }
        try {
            var retryAfterMillis = execute(key, capacity, window, now);
            consecutiveFailures.set(0);
            return retryAfterMillis == 0
                    ? RateLimitAttempt.allowed()
                    : RateLimitAttempt.refused(Duration.ofMillis(retryAfterMillis));
        } catch (DataAccessException e) {
            return degrade(key, now, e);
        }
    }

    private long execute(String key, int capacity, Duration window, long now) {

        var result = redis.execute(script, List.of(key), String.valueOf(capacity), String.valueOf(window.toMillis()),
                String.valueOf(now));
        // A null reply means the script ran but returned nothing, which the script cannot do. Treating it as a
        // failure rather than as zero keeps an impossible state from reading as "allowed".
        if (result == null) {
            throw new IllegalStateException("rate-limit script returned no value for key " + key);
        }
        return result;
    }

    private RateLimitAttempt degrade(String key, long now, DataAccessException cause) {

        if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
            openUntil.set(now + OPEN_DURATION.toMillis());
        }
        log.warn("Rate limit degraded, allowing without shared state: key={} reason={}", key, cause.getMessage());
        return RateLimitAttempt.degraded();
    }
}
