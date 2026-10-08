package dev.axonia.config;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import dev.axonia.ClockTicker;
import dev.axonia.RateLimitAttempt;
import dev.axonia.RateLimitStore;

/**
 * Token buckets in this JVM. Correct for exactly one backend instance, which is the Self-hosted edition's default
 * deployment; more than one instance multiplies every budget by the instance count and needs the Redis store.
 *
 * <p>
 * The arithmetic is transcribed from {@link RedisRateLimitStore}'s Lua script expression by expression, operand order
 * and all, so the two stores make the same decision from the same state: {@code tokens} refills lazily at
 * {@code capacity / window} per millisecond, elapsed time is floored at zero so a clock that steps backward (a leap
 * second, an NTP correction) never refills negatively, {@code fullAt} is at least one millisecond past {@code now} so a
 * bucket is never considered already expired the instant it is written, and the stored {@code tokens} value is rounded
 * to 14 significant digits before being kept, matching what a round trip through Redis does to it.
 *
 * <p>
 * That last rounding is not cosmetic. The script persists tokens with Lua's {@code tostring}, and Lua 5.1 formats a
 * number with C's {@code %.14g} - 14 significant digits, not the full precision of the double. Redis therefore never
 * sees the exact value Java's arithmetic would keep; it sees that value rounded, and every later decision refills from
 * the rounded one. Left unrounded here, the two stores agree almost everywhere and disagree exactly at the boundary
 * that matters: at {@code capacity=1}, {@code window=1h}, one millisecond short of the window both stores agree
 * {@code tokens} is {@code 0.9999997222222222}, correctly {@code REFUSED} - but that decision is what gets stored, and
 * Redis's round trip through {@code tostring} truncates it to {@code "0.99999972222222"}, one digit short of the true
 * value. One more millisecond, and an unrounded store's refill lands on exactly {@code 1.0} ({@code ALLOWED}), while
 * Redis's refill starts from the truncated value and lands a hair under it instead ({@code REFUSED}, for one more
 * millisecond). {@link RedisRateLimitStoreTest} drives identical sequences through both stores to prove this boundary,
 * and others, now agree.
 *
 * <p>
 * {@link BigDecimal#round(MathContext)} with {@link RoundingMode#HALF_EVEN} rather than
 * {@code String.format("%.14g", tokens)}: both agree on every value this class has been tested with, but the
 * {@code BigDecimal} path rounds the double's exact value to 14 significant digits with ties broken to even, which is
 * what a correctly-rounded C {@code %.14g} - and so Lua's {@code tostring} - actually does; {@code String.format}'s
 * floating conversions are documented to round half up instead, a difference that only an exact decimal tie could
 * expose.
 *
 * <p>
 * The buckets are a Caffeine cache on the application's clock, and each one expires the millisecond after its own
 * {@code fullAt}: the in-memory counterpart of the script's {@code PEXPIRE}, since Redis keeps a key through the
 * millisecond its expiry names and drops it after. Dropping a bucket at {@code fullAt} itself would not be equivalent:
 * there the refill starts from the stored, rounded {@code tokens} and can land a hair under {@code capacity}, so Redis
 * can still refuse at that millisecond. One millisecond later the refill always reaches {@code capacity}, so a dropped
 * bucket and a kept one decide alike.
 *
 * <p>
 * At most {@link #MAX_BUCKETS} buckets are held, as Redis bounds its keyspace with {@code maxmemory-policy
 * allkeys-lru}: the keys are client addresses an attacker chooses, and an evicted bucket starts full on its next
 * attempt, which forgives that one key rather than refusing anybody. Caffeine's maintenance runs on a calling thread
 * rather than a pool, so the bound is restored before the call that drains returns and is exceeded only briefly under
 * concurrent writes.
 */
class InMemoryRateLimitStore implements RateLimitStore {

    static final int MAX_BUCKETS = 100_000;

    private record Bucket(double tokens, long at, long fullAt) {
    }

    private final Clock clock;
    private final Cache<String, Bucket> buckets;

    InMemoryRateLimitStore(Clock clock) {

        this.clock = clock;
        this.buckets = Caffeine.newBuilder().maximumSize(MAX_BUCKETS).ticker(new ClockTicker(clock))
                .executor(Runnable::run).expireAfter(Expiry.writing(InMemoryRateLimitStore::keptFor)).build();
    }

    @Override
    public RateLimitAttempt tryConsume(String key, int capacity, Duration window) {

        long now = clock.millis();
        long windowMillis = window.toMillis();
        var outcome = new RateLimitAttempt[1];
        buckets.asMap().compute(key, (k, bucket) -> {
            double tokens = bucket == null
                    ? capacity
                    : Math.min(capacity,
                            bucket.tokens() + (double) Math.max(0, now - bucket.at()) * capacity / windowMillis);
            if (tokens >= 1) {
                outcome[0] = RateLimitAttempt.allowed();
                tokens -= 1;
            } else {
                outcome[0] = RateLimitAttempt
                        .refused(Duration.ofMillis((long) Math.ceil((1 - tokens) * windowMillis / capacity)));
            }
            long fullAt = now + Math.max(1, (long) Math.ceil((capacity - tokens) * windowMillis / capacity));
            return new Bucket(storedTokens(tokens), now, fullAt);
        });
        return outcome[0];
    }

    /**
     * The value actually kept between calls, rounded to 14 significant digits - see the class Javadoc for why.
     * {@code fullAt} above is computed from the unrounded {@code tokens}, matching the script: Redis's own
     * {@code PEXPIRE} argument is likewise taken from {@code tokens} before the {@code HSET} that rounds it.
     */
    private static double storedTokens(double tokens) {

        return new BigDecimal(tokens).round(new MathContext(14, RoundingMode.HALF_EVEN)).doubleValue();
    }

    /** Through the millisecond of {@code fullAt} and no longer - see the class Javadoc. */
    private static Duration keptFor(String key, Bucket bucket) {

        return Duration.ofMillis(bucket.fullAt() - bucket.at() + 1);
    }

    /** The buckets held once Caffeine's maintenance has run; one that expired under a second ago may still count. */
    long size() {

        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
