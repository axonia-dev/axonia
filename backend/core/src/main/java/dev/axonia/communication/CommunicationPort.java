package dev.axonia.communication;

/**
 * The one method a sender calls. A sender owns who cares about a message and what it says; everything about whether a
 * given recipient actually gets it, and how it leaves, is the spine's concern from here on.
 */
public interface CommunicationPort {

    /** Returns the number of outbox rows actually inserted (the outbox's insert-on-conflict idempotency guarantee). */
    int enqueue(OutboundMessage message);
}
