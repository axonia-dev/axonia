package dev.axonia.communication.application;

import java.time.Duration;

/**
 * The configured knobs the outbound pipeline reads: how a drain tick claims, paces and retries, how long the outbox
 * keeps a message, and the unsubscribe signing key. Bound and documented as {@code CommunicationProperties}.
 */
public interface CommunicationSettings {

    int batchSize();

    Duration drainBudget();

    int sendRate();

    int drainWorkers();

    int maxAttempts();

    Duration retryBackoff();

    Duration claimLease();

    Duration wrapUpMaxAge();

    Duration awaitingCloseMaxAge();

    Duration outboxRetention();

    int retentionBatchSize();

    String unsubscribeSecret();
}
