package dev.axonia.events;

import java.time.Instant;

import lombok.Builder;

/**
 * Where an action item is answered. A null {@code sessionId} means the community has no session to point at yet, not
 * that the item owes no answer: every action item owes a checkpoint. {@code pinned} is false when the item late-binds
 * to whatever session comes next, and true when an organizer named a specific one.
 *
 * <p>
 * {@code governingTimeZone} is the zone that governs the checkpoint session's wall clock, already resolved server-side:
 * an occurrence takes its series' {@code timeZone}, a one-off takes its own, and null means no zone is known, which
 * leaves the reader its viewer's. {@link Session} deliberately carries those two as separate components because an edit
 * form has to tell a series' zone from a session's own; a checkpoint is read-only and is only ever asked what calendar
 * day it falls on, so it carries the single resolved answer instead. Carrying the pair here would give every renderer
 * of a checkpoint date its own chance to combine them differently, which is how a chip and the session card behind it
 * came to name two different days.
 */
@Builder
public record Checkpoint(Long sessionId, Instant scheduledAt, boolean pinned, String governingTimeZone) {
}
