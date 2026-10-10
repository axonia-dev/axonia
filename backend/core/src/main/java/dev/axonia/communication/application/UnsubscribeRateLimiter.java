package dev.axonia.communication.application;

import java.time.Duration;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.ratelimit.RateLimitDecision;
import dev.axonia.shared.api.ratelimit.RateLimiter;
import dev.axonia.shared.api.ratelimit.RateLimiters;

/**
 * Abuse backstop for the one anonymous write on the {@code /public/**} surface's communication corner: its job is to
 * cap log spam and a trivially-scripted flood, not to defeat guessing. An {@link UnsubscribeTokens} token is a 256-bit
 * HMAC, not a short code - brute force is not on the table at any budget - so the number below is a round, generous
 * one, not a tightly-reasoned one.
 */
@Component
public class UnsubscribeRateLimiter {

    private static final int ATTEMPTS_PER_MINUTE = 10;

    private final RateLimiter limiter;

    public UnsubscribeRateLimiter(RateLimiters rateLimiters) {

        this.limiter = rateLimiters.create("notifications-unsubscribe", ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1));
    }

    /** Whether the address may attempt one more unsubscribe call, and how long to wait when it may not. */
    public RateLimitDecision allow(String clientAddress) {

        return limiter.tryConsume(clientAddress);
    }
}
