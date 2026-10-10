package dev.axonia.communication.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.application.MailHold;
import dev.axonia.communication.domain.OutboxDelivery;
import dev.axonia.communication.domain.OutboxMessage;
import lombok.Builder;

/**
 * The outbox as the drain works it: claiming the rows due now, recording what became of each, and reading back the
 * message a subject was sent. Writing a message into it is the cross-module {@code CommunicationPort}.
 */
public interface Outbox {

    /** Claims up to {@code batchSize} due rows, oldest first, durably {@code SENDING} once this returns. */
    List<OutboxDelivery> claim(ClaimQuery query);

    /**
     * Writes one chat delivery per destination beside the placeholder {@code row}; re-running it duplicates nothing.
     */
    int expandChatRow(OutboxDelivery row, List<Long> destinationIds, Instant now);

    void markSent(Long id, String organizationId, Instant now);

    void markSuppressed(Long id, String organizationId, String reason, Instant now);

    /** Counts one more failed attempt, abandoning the row once {@code maxAttempts} is reached. */
    void markFailed(Long id, String organizationId, Instant now, int maxAttempts);

    /** Hands claimed rows nobody sent back to {@code PENDING}. */
    int release(List<Long> ids, String organizationId, Instant now);

    /** The one message row for a subject, if a sender already enqueued it. */
    Optional<OutboxMessage> findMessageForSubject(MessageType type, String subjectRef);

    /**
     * A well-named parameter object rather than a six-parameter {@code claim}, which PMD would refuse. {@code mailHeld}
     * leaves every mail row where it is and claims only in-app and chat rows, for the rest of a tick in which the
     * server refused mail and accepted none of a batch (see {@link MailHold}).
     */
    @Builder
    record ClaimQuery(int batchSize, Instant now, int maxAttempts, Duration retryBackoff, Duration claimLease,
            boolean mailHeld) {
    }
}
