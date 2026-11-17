package dev.axonia.communication.api;

/**
 * What a sender calls. A sender owns who cares about a message and what it says; everything about whether a given
 * recipient actually gets it, and how it leaves, is the spine's concern from here on, unless the sender takes the
 * message back before anybody was handed it.
 */
public interface CommunicationPort {

    /** Returns the number of outbox rows actually inserted (the outbox's insert-on-conflict idempotency guarantee). */
    int enqueue(OutboundMessage message);

    /**
     * Takes back the message of {@code type} for {@code subjectRef} wherever no delivery was made yet: each such
     * delivery ends {@link OutboxStatus#SUPPRESSED} as {@link SuppressionReason#WITHDRAWN}. A delivery already sent or
     * in flight stays as it is, so the count returned, of deliveries withdrawn, is zero when the message may already be
     * read.
     */
    int withdrawPending(MessageType type, String subjectRef);
}
