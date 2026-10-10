package dev.axonia.communication.api;

/**
 * Answers what happened to the messages the outbox holds for one subject - the read half of the outbox, alongside
 * {@link CommunicationPort#enqueue}'s write half. {@code events} is the first caller: its session wrap-up card has no
 * honest instant at which "Sent" is true the moment an organizer presses the button, because the write only makes the
 * row eligible and a background sweep acts on it up to a tick later. The missing state - {@code QUEUED} - is derived
 * from this port's counts rather than stored anywhere, because the outbox is the only place that already knows it.
 */
public interface MessageOutcomePort {

    /**
     * Counts, by outcome, every outbox row for {@code type} and {@code subjectRef} in the caller's own organization -
     * see {@link MessageOutcome} for how the outbox's six statuses, crossed with {@code SuppressionReason} for
     * {@code SUPPRESSED} rows, coarsen into the five counts returned. Scoped the same way every other read in this
     * module is: an explicit {@code organization_id} predicate sourced from the {@code app.current_org} GUC, layered on
     * top of Row-Level Security as defence in depth, so an unscoped caller sees {@link MessageOutcome} with every count
     * at zero rather than a thrown exception - see {@code JdbcOutbox}'s class javadoc for the full reasoning. A
     * {@code subjectRef} with no matching rows - never enqueued, or a typo - reports the identical all-zero result; the
     * two cases are indistinguishable from here; and indistinguishable is fine, because "no messages exist for this
     * subject, in this organization" is the true answer to both.
     */
    MessageOutcome outcomeFor(MessageType type, String subjectRef);
}
