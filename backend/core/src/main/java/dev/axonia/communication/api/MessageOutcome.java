package dev.axonia.communication.api;

import lombok.Builder;

/**
 * The outbox's answer to "what happened to the messages for this subject", counted rather than listed: a session's
 * wrap-up card needs a number to put next to "could not be reached", never the identities behind it.
 *
 * <p>
 * The five counts are a deliberate coarsening of the outbox's six {@code OutboxStatus} values crossed with
 * {@code SuppressionReason}, not an omission. {@code pending} answers "is any recipient's row still short of a terminal
 * outcome" - it folds together {@code PENDING} (never attempted), {@code SENDING} (claimed, in flight), and
 * {@code FAILED} (a send that failed and is waiting out its retry backoff before the sweep reclaims it): none of the
 * three is a resting place, every one of them still becomes {@code SENT}, {@code SUPPRESSED}, or {@code ABANDONED} on a
 * later tick. A caller deriving a coarser state from these counts - {@code events}' session read model does exactly
 * this for its {@code QUEUED} state - can treat {@code pending() > 0} as "still queued" and every row accounted for
 * once {@code pending() == 0}, without ever naming {@code SENDING} or {@code FAILED} itself.
 *
 * <p>
 * {@code SUPPRESSED} rows split in two, because they are not the same fact. {@code suppressed} counts every
 * {@code SUPPRESSED} row whose {@code suppressed_reason} is one of {@code DeliveryGate}'s governance reasons -
 * {@code ORGANIZATION_OFF}, {@code SUBJECT_OFF}, {@code MEMBER_OPTED_OUT}, {@code NOT_A_MEMBER}, {@code UNKNOWN_TYPE}:
 * the system worked exactly as configured, and for {@code MEMBER_OPTED_OUT} in particular, that count discloses a
 * private choice a member made in their own notification settings back to the organizer who mailed them - not this
 * port's decision to make, but a caller's to guard against surfacing. {@code unreachable} counts only
 * {@code SUPPRESSED} rows reasoned {@code NO_ADDRESS} or {@code MAIL_NOT_CONFIGURED}: {@code identity} resolved no
 * usable address for that recipient, or the installation had no mail server to send through, the two cases that are a
 * delivery failure rather than a delivery choice. The two are counted separately and never summed by this port,
 * precisely so a caller can surface one and withhold the other - {@code events}' wrap-up card does exactly that,
 * folding {@code unreachable} into "could not be reached" and never reading {@code suppressed} at all. {@code sent} and
 * {@code abandoned} are each exactly one terminal status.
 *
 * <p>
 * A subject with no outbox rows at all - never enqueued, or enqueued under a {@code subjectRef} that does not match -
 * reports all five counts as zero, never an empty or null result: {@link MessageOutcomePort#outcomeFor} is meant to be
 * branched on directly, and a null here would turn into a caller's {@code NullPointerException} rather than the honest
 * "nothing has happened yet" it actually means.
 */
@Builder
public record MessageOutcome(int pending, int sent, int suppressed, int unreachable, int abandoned) {
}
