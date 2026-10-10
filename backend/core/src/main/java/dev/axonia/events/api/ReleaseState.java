package dev.axonia.events.api;

/**
 * The four states a session's release of one message can honestly be in, the same table for the wrap-up and the
 * upcoming notice, read against a different message type. Derived at read time from the stored release intent and the
 * outbox's own counts for the session, never stored: there is no column that could hold {@code QUEUED} without going
 * stale the instant the sweep moves a row on, so the read model recomputes it every time instead.
 *
 * <p>
 * {@code OFF} and {@code SCHEDULED} both mean "the outbox has never heard of this session" - they differ only in
 * whether an organizer has picked a release instant. {@code QUEUED} is the state {@code ReleasePanel} used to skip
 * entirely: pressing "Send now" only makes the outbox rows eligible, and a background sweep acts on them up to a tick
 * later, so there is no honest instant at which the send can be reported as done. {@code SENT} is reached once every
 * row the outbox ever created for this session has reached a terminal outcome, whether or not every recipient was
 * actually reached - see {@link ReleaseStatus#couldNotReach()} for that count.
 *
 * <p>
 * A release instant already in the past with no outbox rows yet reads {@code SCHEDULED}, never {@code SENT}: the
 * producer sweep may simply not have ticked. That holds for both message types, and is why nothing in this derivation
 * consults a clock.
 */
public enum ReleaseState {
    OFF, SCHEDULED, QUEUED, SENT
}
