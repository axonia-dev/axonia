package dev.axonia.recognition.application;

import java.time.Duration;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.ratelimit.RateLimitDecision;
import dev.axonia.shared.api.ratelimit.RateLimiter;
import dev.axonia.shared.api.ratelimit.RateLimiters;

/**
 * Abuse backstop for proposing a reaction. Every proposal validates and uploads an image before the row is even
 * attempted, so even a proposal that never lands - a duplicate name, a full queue - already cost a storage write; this
 * caps how many of those a single member can trigger.
 *
 * <p>
 * Ten an hour is a round, generous number, not a tightly-reasoned one:
 * {@link dev.axonia.recognition.application.ReactionCatalogService} already caps a member at three concurrently open
 * proposals, so this budget is never the thing standing between a member and normal use - it only turns away a script.
 */
@Component
public class ReactionProposalRateLimiter {

    private static final int PROPOSALS_PER_HOUR = 10;

    private final RateLimiter limiter;

    public ReactionProposalRateLimiter(RateLimiters rateLimiters) {

        this.limiter = rateLimiters.create("reaction-proposals", PROPOSALS_PER_HOUR, Duration.ofHours(1));
    }

    /** Whether the caller may propose one more reaction, and how long to wait when they may not. */
    public RateLimitDecision allow(String userId) {

        return limiter.tryConsume(userId);
    }
}
