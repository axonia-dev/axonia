package dev.axonia;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dev.axonia.shared.api.ratelimit.RateLimitAttempt;
import dev.axonia.shared.api.ratelimit.RateLimitStore;

/**
 * A rate-limit store that counts, for the tests that assert what a limiter decides rather than how the arithmetic
 * works. The arithmetic lives in Lua and is covered by {@code RedisRateLimitStoreTest} against a real Redis.
 *
 * <p>
 * The refusal wait is {@code window / capacity}. That is not a second copy of the refill maths: it is the
 * fully-spent-bucket case, which is the only case a fixed clock can produce, and it is what the suite's three exact
 * {@code Retry-After} assertions already expect - 60 seconds for the capacity-1 MCP call budget, 6 for the capacity-10
 * redeem budget, 12 for the capacity-5 export budget.
 */
public final class FakeRateLimitStore implements RateLimitStore {

    private final Map<String, Integer> consumed = new ConcurrentHashMap<>();
    private boolean degraded;

    /** Makes every later attempt behave as though Redis were unreachable. */
    public void degrade() {

        degraded = true;
    }

    /** The keys the limiters actually wrote, so a test can assert how they were namespaced. */
    public Set<String> keys() {

        return Set.copyOf(consumed.keySet());
    }

    @Override
    public RateLimitAttempt tryConsume(String key, int capacity, Duration window) {

        if (degraded) {
            return RateLimitAttempt.degraded();
        }
        return consumed.merge(key, 1, Integer::sum) <= capacity
                ? RateLimitAttempt.allowed()
                : RateLimitAttempt.refused(window.dividedBy(capacity));
    }
}
