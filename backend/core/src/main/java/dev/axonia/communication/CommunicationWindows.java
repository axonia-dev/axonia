package dev.axonia.communication;

import java.time.Duration;

/**
 * How far into the past a producer may still look when deciding whether something is due - the ceiling that keeps a
 * producer from re-examining every record it has ever cared about, forever, on every tick, and from mailing a member
 * who just joined about every session a community has ever held. Declared here because the defaults live alongside
 * {@code communication}'s other tuning knobs (batch size, retry backoff, and so on) in
 * {@code communication.internal.CommunicationProperties}; implemented there for the same reason, and consumed by
 * whichever bounded context's producer needs a floor - today, {@code events}' {@code WrapUpProducer} and
 * {@code AwaitingCloseProducer}. {@code events} already depends on {@code communication} to call
 * {@link CommunicationPort#enqueue}, so a caller in {@code events.internal} adds no new module edge.
 */
public interface CommunicationWindows {

    /**
     * How long after a held session's {@code wrapUpReleaseAt} the wrap-up producer will still consider it due. Without
     * this floor, {@code findWrapUpSessionIds} matches any released session forever, and a brand-new member's outbox
     * row for a session from a year ago inserts cleanly - there is no earlier row to conflict with - the very first
     * tick after they join.
     */
    Duration wrapUpMaxAge();

    /**
     * How long past a scheduled session's own {@code scheduledAt} the awaiting-close producer will still nudge about
     * it. Without this floor, a newly promoted organizer is flooded with nudges for every session the community has
     * ever left open, on the very next tick.
     */
    Duration awaitingCloseMaxAge();
}
