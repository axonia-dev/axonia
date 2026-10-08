/**
 * How a session surface behaves, derived from the session's status.
 *
 * - `contribute` - an upcoming session: sign up, add slots, RSVP.
 * - `capture` - a held session: contribution is closed; attach resources to slots.
 * - `edit` - a held session an organizer has explicitly opened for editing: correct what happened, or backfill a
 *   session that was held before the community moved to axonia. Read-only is the default; this is the deliberate
 *   opt out of it.
 * - `readonly` - a skipped session: no contribution, no resource capture.
 */
export type SessionMode = "contribute" | "capture" | "edit" | "readonly";

/**
 * Whether a mode gets the structural affordances of an active schedule (add/edit/remove/reorder
 * a slot): an upcoming session being contributed to, or a held one an organizer opened to correct
 * or backfill it. Narrower than "the session can be touched at all" - RSVP/attendance is genuinely
 * about *future* contribution and stays gated on `mode === "contribute"` alone. Presenter membership
 * (join, invite, withdraw, remove) is a different affordance again: it lives on the presenter stack
 * and is available in every mode except `readonly`.
 */
export function isShapingMode(mode: SessionMode): boolean {
  return mode === "contribute" || mode === "edit";
}
