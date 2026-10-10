package dev.axonia.events.api;

import lombok.Builder;

/**
 * Everything a session's release card needs to render itself honestly, for whichever of the two member-facing messages
 * it is showing: the state ({@link ReleaseState}), how many members would receive it, and - once sending has actually
 * happened - how many did. One record rather than one per message type, because the wrap-up card and the reminder card
 * are the same card asking the same four questions of a different {@code MessageType}.
 *
 * <p>
 * {@code recipientCount} is the community's current membership, resolved through {@code CommunityAccess.memberUserIds}
 * at read time, not the roster the outbox enqueued against. The two can diverge (a member who left after the mail went
 * out, one who joined after and was never enqueued - a known limit), and the card's "to N members" line is honestly
 * answering "how many would this reach today", not "how many did it reach".
 *
 * <p>
 * {@code sent} and {@code couldNotReach} are live counts, not gated on the state being {@link ReleaseState#SENT}: a
 * batch the sweep is midway through draining reads {@code QUEUED} with a nonzero {@code sent} already, and that is the
 * honest picture, not a bug to hide - the two counts simply mean nothing worth showing before the outbox has any
 * terminal rows at all ({@code OFF}/{@code SCHEDULED}: no rows; {@code QUEUED}: rows exist but not every one has
 * finished yet). {@code couldNotReach} folds together the outbox's {@code ABANDONED} (every send attempt failed) and
 * {@code MessageOutcome#unreachable()} ({@code NO_ADDRESS} or {@code MAIL_NOT_CONFIGURED}: no usable address to send
 * to, or no mail server to send through) rows - "how many recipients could not be reached", which the outbox has always
 * recorded and nothing has ever surfaced. Both are genuine delivery failures; deliberately excluded is
 * {@code MessageOutcome#suppressed()} - a member's own opt-out, a community or organization switched off, an
 * unrecognized message type - which is governance working as designed, not a failure, and would otherwise disclose a
 * private preference back to the organizer reading the card. Which of the two *included* reasons applies to which
 * recipient is a personal-data surface this record deliberately does not carry.
 */
@Builder
public record ReleaseStatus(ReleaseState state, int recipientCount, int sent, int couldNotReach) {
}
