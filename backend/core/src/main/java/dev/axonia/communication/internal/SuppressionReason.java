package dev.axonia.communication.internal;

import java.util.List;

/**
 * Why an outbox row was never sent. {@link #NO_ADDRESS} is {@code OutboxRowProcessor}'s, for a row {@link DeliveryGate}
 * admitted but for which {@code identity} resolved no usable address, and {@link #EXPANDED} marks a chat placeholder
 * that fanned out; {@link #NO_DESTINATION} is written by both, by {@code OutboxRowProcessor} for a placeholder that
 * found no destination to fan out to; every other reason is {@link DeliveryGate}'s own. Recorded on the row rather than
 * deleting it, which doubles as the answer to "why did I not get this".
 *
 * <p>
 * {@link #UNREACHABLE} names the two a sender reads as "could not be reached": nobody chose them, and no setting a
 * member owns caused them.
 *
 * <p>
 * {@link #SUBJECT_OFF} covers a community switching a session message off and an organization switching its own roundup
 * off, which is why it names neither: the check asks whether the subject this message is about has this type disabled,
 * and only the module owning that kind of subject knows what that means.
 *
 * <p>
 * {@link #NO_DESTINATION} and {@link #DESTINATION_BROKEN} are a chat row's own, in place of {@link #MEMBER_OPTED_OUT}
 * and {@link #NOT_A_MEMBER}, which a room has no equivalent of.
 */
enum SuppressionReason {
    ORGANIZATION_OFF, SUBJECT_OFF, MEMBER_OPTED_OUT, NOT_A_MEMBER, UNKNOWN_TYPE, NO_ADDRESS,
    // No destination has ever been configured for this community's chat: an operator sees this and goes to add one.
    NO_DESTINATION,
    // A destination was configured but chat delivery gave up on it: an operator sees this and goes to reconnect it.
    DESTINATION_BROKEN,
    /**
     * The placeholder chat row a message is enqueued with, once it has resolved the community's destinations and
     * written one real delivery row for each. Terminal and never sent: it is a fan-out instruction, not a post.
     */
    EXPANDED,
    // A mail row met an installation with no mail server: an operator sees this and goes to configure one.
    MAIL_NOT_CONFIGURED;

    static final List<String> UNREACHABLE = List.of(NO_ADDRESS.name(), MAIL_NOT_CONFIGURED.name());
}
