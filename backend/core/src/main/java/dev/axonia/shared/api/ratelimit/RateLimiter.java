package dev.axonia.shared.api.ratelimit;

import dev.axonia.shared.api.web.ClientAddress;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-key token-bucket rate limiter. Keys are caller identities - a token id, a JWT subject, or a client address
 * resolved by {@link ClientAddress}.
 *
 * <p>
 * State lives in a {@link RateLimitStore} shared by every instance, so a budget is a budget rather than a budget per
 * replica. That was not true before: buckets sat in an in-memory map inside the JVM, and that was accepted because the
 * fix then in mind put a database round trip on the anonymous authentication-failure path. A Redis round trip is not
 * that, which is what made this solvable.
 *
 * <p>
 * Keys are namespaced {@code ratelimit:<name>:<key>} before they reach the store. This is load-bearing rather than
 * tidy: each limiter used to own a private map, so one address was a separate bucket in each, and an un-namespaced key
 * in one shared keyspace would let a flood against one limiter spend the same caller's budget on another.
 *
 * <p>
 * A refusal logs one WARN naming the limiter and the key it refused. The counters say a limit was hit; only the log
 * says who hit it, and no call site logs its own refusal. Allowed calls stay silent, so the line means something when
 * it appears. A degraded decision - the store could not be reached, so the call was allowed without consulting shared
 * state - is counted and logged apart from both, because a guard rail that has quietly stopped guarding is worse than
 * one that is known to be down.
 *
 * <p>
 * {@link #tryConsumePaced} is the exception, and it exists because that WARN's whole value is that it is rare. For a
 * caller whose answer to a refusal is to wait out {@code retryAfter} and ask again, a refusal is not an anomaly at all;
 * it is the mechanism working, several times a second, for as long as there is a backlog. Such a caller counts its
 * refusals under {@code outcome=paced} and logs none of them, so both the WARN and {@code outcome=refused} keep meaning
 * "somebody hit a limit and was turned away" for every other caller.
 *
 * <p>
 * Every limiter's {@code name} must be unique across the application, and must not contain a colon. Uniqueness is the
 * identity of the Micrometer counter triple, so two limiters sharing a name would merge their metrics into one line
 * that reads plausibly and describes neither, and it is now also the identity of the keyspace, so they would merge
 * their buckets too. The colon rule keeps the key prefix unambiguous.
 */
@Slf4j
public final class RateLimiter {

    private static final String METER = "axonia.rate_limit";

    private final String name;
    private final String keyPrefix;
    private final Budget budget;
    private final RateLimitStore store;
    private final Counter allowed;
    private final Counter refused;
    private final Counter paced;
    private final Counter degraded;

    public RateLimiter(String name, Budget budget, RateLimitStore store, MeterRegistry meters) {

        if (name.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "limiter name '%s' contains a colon, which would make its Redis key prefix ambiguous"
                            .formatted(name));
        }
        this.name = name;
        this.keyPrefix = "ratelimit:" + name + ":";
        this.budget = budget;
        this.store = store;
        this.allowed = counter(meters, name, "allowed");
        this.refused = counter(meters, name, "refused");
        this.paced = counter(meters, name, "paced");
        this.degraded = counter(meters, name, "degraded");
    }

    public RateLimitDecision tryConsume(String key) {

        var attempt = store.tryConsume(keyPrefix + key, budget.limit(), budget.window());
        return switch (attempt.outcome()) {
            case ALLOWED -> allow();
            case DEGRADED -> degrade(key);
            case REFUSED -> {
                refused.increment();
                var refusal = RateLimitDecision.refused(attempt.retryAfter());
                log.warn("Rate limit exhausted: limiter={} key={} retryAfterSeconds={}", name, key,
                        refusal.retryAfterSeconds());
                yield refusal;
            }
        };
    }

    /**
     * For a caller that answers a refusal by waiting out {@code retryAfter} and asking again, rather than by turning
     * somebody away. Identical arithmetic, identical decision; the difference is that the refusal is counted under
     * {@code outcome=paced} and never logged.
     *
     * <p>
     * The outbound-mail drain is the one such caller. It spins deliberately at the vendor's ceiling, so several
     * refusals per message sent is what success looks like there: routed through {@link #tryConsume} it would emit tens
     * of WARN lines a second for the whole of a large fan-out, and would leave {@code outcome=refused} reading "the
     * drain is busy" rather than "a limit turned somebody away". A degraded decision is still logged, on this path as
     * on the other: shared state that cannot be reached is an anomaly however routine a refusal is.
     */
    public RateLimitDecision tryConsumePaced(String key) {

        var attempt = store.tryConsume(keyPrefix + key, budget.limit(), budget.window());
        return switch (attempt.outcome()) {
            case ALLOWED -> allow();
            case DEGRADED -> degrade(key);
            case REFUSED -> {
                paced.increment();
                yield RateLimitDecision.refused(attempt.retryAfter());
            }
        };
    }

    private RateLimitDecision allow() {

        allowed.increment();
        return RateLimitDecision.allow();
    }

    private RateLimitDecision degrade(String key) {

        degraded.increment();
        log.warn("Rate limit not enforced, shared state unavailable: limiter={} key={}", name, key);
        return RateLimitDecision.allow();
    }

    private static Counter counter(MeterRegistry meters, String name, String outcome) {

        return Counter.builder(METER).tag("limiter", name).tag("outcome", outcome).register(meters);
    }
}
