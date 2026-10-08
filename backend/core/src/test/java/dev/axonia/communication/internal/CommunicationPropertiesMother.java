package dev.axonia.communication.internal;

import java.time.Duration;

import lombok.experimental.UtilityClass;

/**
 * A valid {@link CommunicationProperties} fixture for tests that need one but do not care about its exact values -
 * {@link SmtpDeliveryTest}, {@link OutboxDrainTest} and {@link UnsubscribeTokensTest} otherwise each carried their own
 * copy of the same ten-argument constructor call.
 */
@UtilityClass
class CommunicationPropertiesMother {

    private static final Duration DEFAULT_MAX_AGE = Duration.ofDays(30);
    private static final Duration DEFAULT_DRAIN_BUDGET = Duration.ofSeconds(45);
    private static final int DEFAULT_SEND_RATE = 10;
    private static final Duration DEFAULT_OUTBOX_RETENTION = Duration.ofDays(90);
    private static final int DEFAULT_RETENTION_BATCH_SIZE = 200;

    CommunicationProperties any() {

        return withSecret("test-unsubscribe-secret");
    }

    CommunicationProperties withSecret(String secret) {

        return withWindows(DEFAULT_MAX_AGE, DEFAULT_MAX_AGE, secret);
    }

    /** For {@link CommunicationWindowsAdapterTest}, which needs the two windows distinguishable from each other. */
    CommunicationProperties withWindows(Duration wrapUpMaxAge, Duration awaitingCloseMaxAge) {

        return withWindows(wrapUpMaxAge, awaitingCloseMaxAge, "test-unsubscribe-secret");
    }

    /** For {@link OutboxDrainTest}, which needs a budget already spent to prove the loop never even claims. */
    CommunicationProperties withDrainBudget(Duration drainBudget) {

        return withWindows(DEFAULT_MAX_AGE, DEFAULT_MAX_AGE, "test-unsubscribe-secret", drainBudget, DEFAULT_SEND_RATE);
    }

    private CommunicationProperties withWindows(Duration wrapUpMaxAge, Duration awaitingCloseMaxAge, String secret) {

        return withWindows(wrapUpMaxAge, awaitingCloseMaxAge, secret, DEFAULT_DRAIN_BUDGET, DEFAULT_SEND_RATE);
    }

    private CommunicationProperties withWindows(Duration wrapUpMaxAge, Duration awaitingCloseMaxAge, String secret,
            Duration drainBudget, int sendRate) {

        return new CommunicationProperties(100, drainBudget, sendRate, 4, 5, Duration.ofMinutes(5),
                Duration.ofMinutes(5), wrapUpMaxAge, awaitingCloseMaxAge, DEFAULT_OUTBOX_RETENTION,
                DEFAULT_RETENTION_BATCH_SIZE, secret);
    }
}
