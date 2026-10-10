package dev.axonia.communication.application;

import dev.axonia.communication.api.MessageType;

/**
 * What a valid, unforged unsubscribe token proves: which member, in which Organization, wants which {@link MessageType}
 * turned off.
 *
 * <p>
 * {@code organizationId} rides along beside the user id and the message type.
 * {@code NotificationPreferenceRepository}'s own convention - the same one {@code communication_preference}'s unique
 * key encodes - is that one {@code user_id} can legitimately hold a separate preference row in more than one
 * Organization; a token that could not name which one would either guess (wrong the moment a member belongs to two CoPs
 * with different settings) or have to flip the preference in every Organization the subject happens to belong to
 * (silently touching communities the click was never about). Carrying the id the outbox row already had at send time
 * costs nothing and removes the ambiguity outright.
 */
public record UnsubscribeClaim(String userId, String organizationId, MessageType type) {
}
