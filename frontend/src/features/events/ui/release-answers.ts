/**
 * The three answers, as the mutations that apply them.
 *
 * This is a parameter and not a single "set the release instant" call because the two message
 * types deliberately post different payloads: the wrap-up's "now" is the browser's clock, which
 * survives only because its server clamps a past instant up to its own, while the server
 * refuses a past instant for the reminder outright - so the reminder says `{"sendNow": true}` and
 * lets the server stamp its own clock, taking skew out of the contract. Unifying the two would
 * make a reminder sent from a browser running two seconds slow fail as a pick in the past.
 *
 * Each implementation owns its own error reporting, per the convention that a failed mutation
 * surfaces through `notifyError` at the call site. `scheduleAt` takes the one callback the panel
 * genuinely needs back: the dialog stays open on failure, so only a confirmed success may close it.
 */
export interface ReleaseActions {
  isPending: boolean;
  reset: () => void;
  sendNow: () => void;
  scheduleAt: (releaseAt: string, onScheduled: () => void) => void;
  suppress: () => void;
}

/** The words one message type puts on the shared panel. Everything not named here reads the same
 *  for every message ("Send it now", "Pick a time"), so it stays in the component. */
export interface ReleaseMenuCopy {
  note: string;
  offHint: string;
  dialogTitle: string;
  fieldLabel: string;
}

/**
 * What this message type lets a reader do about its release: the current intent, the words, and
 * the mutations.
 *
 * `SessionRelease` says what is true about a release; this says what can be done about it. They
 * are separate because the first is derived purely from the session and the second needs the
 * mutation hooks of one specific endpoint.
 */
export interface ReleaseAnswerSet {
  releaseAt: string | null;
  copy: ReleaseMenuCopy;
  actions: ReleaseActions;
  /** The latest instant this message type's release may honestly sit at. Omitted for a message
   *  type with no such bound, which is the wrap-up. */
  latestReleaseAt?: string;
}
