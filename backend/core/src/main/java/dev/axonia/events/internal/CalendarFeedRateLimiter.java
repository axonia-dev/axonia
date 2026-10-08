package dev.axonia.events.internal;

import java.time.Duration;

import org.springframework.stereotype.Component;

import dev.axonia.RateLimitDecision;
import dev.axonia.RateLimiter;
import dev.axonia.RateLimiters;

/**
 * Abuse backstop for the anonymous calendar feed, on the same grounds {@code UnsubscribeRateLimiter} documents for the
 * other unauthenticated endpoint on the {@code /public/**} surface: its job is to cap a trivially-scripted flood, not
 * to defeat guessing. A {@link SubscriptionTokens} token is 32 random bytes, so brute force is not on the table at any
 * budget, and both numbers below are round, generous ones rather than tightly-reasoned ones.
 *
 * <p>
 * Two budgets, because either alone leaves the case the other covers wide open.
 *
 * <p>
 * The per-caller budget is the one that actually caps a flood, and it is spent first. A script picking a fresh random
 * token per request shares no per-subscription bucket with itself, so a token-keyed budget would never refuse it while
 * every request still cost a shared-store round trip and an indexed lookup - the key has to be one the caller does not
 * choose. It is generous because it is shared: a whole enterprise polling from one office egress address arrives here
 * as a single caller, and the document advertises {@code REFRESH-INTERVAL:PT1H}, so sixty a minute leaves an
 * implausibly large office comfortable while holding a script to one request a second.
 *
 * <p>
 * The per-subscription budget covers what the other cannot: an abusive client holding one leaked URL, whose address
 * changes. Ten a minute is already far above what every calendar a member owns needs between them, manual refreshes
 * included.
 */
@Component
public class CalendarFeedRateLimiter {

    private static final int FETCHES_PER_MINUTE_PER_CALLER = 60;
    private static final int FETCHES_PER_MINUTE_PER_SUBSCRIPTION = 10;

    private final RateLimiter perCaller;
    private final RateLimiter perSubscription;

    public CalendarFeedRateLimiter(RateLimiters rateLimiters) {

        this.perCaller = rateLimiters.create("calendar-feed-caller", FETCHES_PER_MINUTE_PER_CALLER,
                Duration.ofMinutes(1));
        this.perSubscription = rateLimiters.create("calendar-feed", FETCHES_PER_MINUTE_PER_SUBSCRIPTION,
                Duration.ofMinutes(1));
    }

    /**
     * Whether this caller may fetch once more, whatever it is fetching, and how long to wait when it may not.
     *
     * <p>
     * The address comes from {@code ClientAddress}, which reads the header the edge stamps rather than anything the
     * caller supplies. {@code EdgeGateFilter} refuses every request that did not arrive through that edge, which is
     * what makes the key trustworthy on an endpoint with no credential of its own.
     */
    public RateLimitDecision allowCaller(String clientAddress) {

        return perCaller.tryConsume(clientAddress);
    }

    /**
     * Whether this subscription may be fetched once more, and how long to wait when it may not.
     *
     * <p>
     * Keyed on the hash rather than on the token itself. The key becomes a Redis key and the subject of the limiter's
     * refusal WARN, and the token is the credential the whole feed is authorized by: nothing that is not already in the
     * database may carry it there.
     */
    public RateLimitDecision allowSubscription(String token) {

        return perSubscription.tryConsume(SubscriptionTokens.hash(token));
    }
}
