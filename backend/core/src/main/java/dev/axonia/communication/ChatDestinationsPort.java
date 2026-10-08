package dev.axonia.communication;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Where a message's chat posts go, and what became of each. Declared here and implemented in {@code events.internal}:
 * only the module owning a kind of subject can walk an opaque {@code subjectRef} to the community whose destinations
 * these are, and {@code events} already depends on {@code communication} to enqueue, so an adapter there adds no module
 * edge and no cycle.
 *
 * <p>
 * {@link #destinationsFor} is asked once per message, at expansion. Everything below it is asked once per destination,
 * by id: a delivery row already names the destination it is for, so nothing after expansion resolves a subject again.
 */
public interface ChatDestinationsPort {

    /** Which message types post to a chat destination at all. Answered by the module that owns those subjects. */
    Set<MessageType> channelTypes();

    /**
     * The destinations this subject's post should fan out to right now, resolved at drain rather than at enqueue: a
     * destination added inside a message's lead window must still receive it. Empty for a subject that resolves to
     * nothing, and for a community with no destinations.
     */
    List<Long> destinationsFor(String organizationId, String subjectRef);

    ChatChannelStatus statusFor(String organizationId, long destinationId);

    void recordDelivered(String organizationId, long destinationId, Instant at);

    void recordFailure(String organizationId, long destinationId, ChatFailure failure);

    /** One failed attempt at a destination, as the module that owns it needs to record it. */
    record ChatFailure(ChatFailureKind kind, String error, Instant at) {
    }
}
