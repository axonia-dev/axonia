package dev.axonia.installation.application;

import java.time.Duration;

import org.springframework.stereotype.Component;

import dev.axonia.shared.api.ratelimit.RateLimitDecision;
import dev.axonia.shared.api.ratelimit.RateLimiter;
import dev.axonia.shared.api.ratelimit.RateLimiters;

/** Ten test mails per person per ten minutes, per instance with the memory store, shared with Redis. */
@Component
class MailServerTestRateLimiter {

    private static final int TESTS_PER_WINDOW = 10;

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final RateLimiter limiter;

    MailServerTestRateLimiter(RateLimiters rateLimiters) {

        this.limiter = rateLimiters.create("mail-server-test", TESTS_PER_WINDOW, WINDOW);
    }

    RateLimitDecision allow(String subject) {

        return limiter.tryConsume(subject);
    }
}
