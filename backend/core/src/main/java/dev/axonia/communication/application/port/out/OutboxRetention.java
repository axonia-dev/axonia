package dev.axonia.communication.application.port.out;

import java.time.Instant;

/**
 * The retention pass over the outbox: a message written before {@code cutoff} expires whole, its record stamped
 * {@code now}, or is left entirely alone while any of its deliveries is still in flight.
 */
public interface OutboxRetention {

    /** Expires up to {@code batchSize} messages of the current organization, answering how many went. */
    int expire(Instant now, Instant cutoff, int batchSize);
}
