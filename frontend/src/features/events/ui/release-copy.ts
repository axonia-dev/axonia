import type { ReleaseStatus, Session } from "@/features/events/domain/session";
import { relativeTime } from "@/lib/period";

// A bare instant already carries its own offset ("Z"), so converting it to the viewer's own
// wall clock and labelling that zone is the honest rendering - it's what the organizer's own
// clock will say when the message actually goes out. Do not "fix" this to a forced UTC render:
// that would be correct but foreign, exactly the kind of surprise this card exists to prevent.
export function formatReleaseInstant(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZoneName: "short",
  });
}

function memberCount(n: number): string {
  return `${n} ${n === 1 ? "member" : "members"}`;
}

export interface ReleaseCopy {
  statement: string;
  subline: string;
  /**
   * Set where the copy reports that the message will not go out after all, which the stored
   * state cannot express on its own: a stranded reminder is still `SCHEDULED`. `ReleaseCard`
   * answers it with its own icon and tone, so this module stays free of anything visual and
   * every state that does not set it keeps the visual its state has always had.
   */
  needsAttention?: boolean;
}

/**
 * What one message type is called, in the words members and organizers already know it by.
 * The wrap-up is "the wrap-up"; the upcoming notice is "the reminder", the noun the community
 * communication settings, the organization kill switch and the member's own preference all
 * use for it.
 *
 * Only the `OFF` subline genuinely differs beyond the noun - turning a wrap-up on sends one,
 * turning a reminder on reminds - so it is the one line each type spells out for itself.
 * Everything else reads identically once the noun is substituted, which is why the four state
 * functions below are shared rather than written twice.
 */
interface ReleaseVoice {
  noun: string;
  offSubline: (recipients: string) => string;
}

const WRAP_UP: ReleaseVoice = {
  noun: "wrap-up",
  offSubline: (recipients) => `Turn it on to send one to ${recipients}.`,
};

const REMINDER: ReleaseVoice = {
  noun: "reminder",
  offSubline: (recipients) => `Turn it on to remind ${recipients}.`,
};

/**
 * The stored release intent, when the session's own start no longer supports it: a reschedule
 * is how that happens, and it is the one disagreement this card exists to report.
 *
 * The backend deliberately preserves an intent an organizer set by hand rather than
 * correcting it, on the explicit understanding that the card says so. Nothing else will:
 * `findUpcomingNoticeSessionIds` needs `scheduled_at > now` AND `upcoming_release_at <= now` at
 * the same time, which is unsatisfiable once the intent sits at or after the start, so such a
 * reminder can never be claimed and never fires. Left unreported it reads as an ordinary
 * schedule right up to the moment the card is replaced by "Did this happen?".
 *
 * Returns the bound rather than a boolean so callers can name it in the copy without having to
 * prove to the type system that it is set. `>=` matches `UpcomingReleaseWindow`, which admits
 * an instant strictly before the start.
 *
 * The bound is undefined for a message type that has none, which is the wrap-up: its release
 * instant is not bounded by anything the session knows, so every rule below is inert for it.
 */
function strandedAfterStart(
  releaseAt: string | null,
  latestReleaseAt: string | undefined,
): string | null {
  if (releaseAt === null || latestReleaseAt === undefined) {
    return null;
  }
  return new Date(releaseAt) >= new Date(latestReleaseAt)
    ? latestReleaseAt
    : null;
}

/**
 * The instant a `SENT` card may quote as the moment the message went out, or null for none it
 * can honestly name.
 *
 * The state comes from the outbox and the instant comes from the stored intent, and a
 * reschedule moves the intent while leaving the terminal outbox row alone. The stored instant
 * can therefore end up describing a send that has already happened with a date that has not
 * arrived, which is how "The reminder went out Sat, Oct 17" appears on the 28th of September.
 * When the two disagree the date is the part that is wrong, so it is dropped: `sentCopy`
 * already has instant-free wording for a message it cannot date.
 *
 * Of the two disqualifications, only the bound one is a message type's own business. "A send
 * cannot have happened at an instant that has not arrived" needs no bound and is applied to
 * every message type, which is why the wrap-up gets it too even though nothing can reach it
 * there today: its release is clamped to now when set, and no path turns it `SENT` early.
 */
function sentAt(
  releaseAt: string | null,
  latestReleaseAt: string | undefined,
  now: Date,
): string | null {
  if (
    releaseAt === null ||
    strandedAfterStart(releaseAt, latestReleaseAt) !== null
  ) {
    return null;
  }
  return new Date(releaseAt) > now ? null : releaseAt;
}

function offCopy(voice: ReleaseVoice, status: ReleaseStatus): ReleaseCopy {
  // Deliberately silent about *why* it is off: a session created inside its own lead window
  // and one an organizer switched off are the same stored null, so any explanation offered
  // here would be a lie in one of the two cases.
  return {
    statement: `No ${voice.noun} for this session.`,
    subline: voice.offSubline(memberCount(status.recipientCount)),
  };
}

function scheduledCopy(
  voice: ReleaseVoice,
  releaseAt: string | null,
  status: ReleaseStatus,
  latestReleaseAt: string | undefined,
): ReleaseCopy {
  const stranded = strandedAfterStart(releaseAt, latestReleaseAt);
  if (stranded !== null) {
    // States the fact and the remedy, and nothing about who moved what: the organizer who set
    // this time and the one who moved the session are often not the same person, and the card
    // has no way to tell. `Change` stays available in this state, so both remedies are one
    // click away from the sentence naming them.
    return {
      statement: `The ${voice.noun} is set for after the session starts.`,
      subline: `It will not go out. Pick a time before ${formatReleaseInstant(stranded)}, or send it now.`,
      needsAttention: true,
    };
  }
  if (releaseAt === null) {
    // Never actually reached (SCHEDULED implies a release instant is set), but keeps this
    // honest rather than throwing on a shape the backend contract doesn't allow.
    return {
      statement: `The ${voice.noun} is scheduled.`,
      subline: `To ${memberCount(status.recipientCount)}.`,
    };
  }
  return {
    statement: `The ${voice.noun} goes out ${formatReleaseInstant(releaseAt)}.`,
    subline: `To ${memberCount(status.recipientCount)} · ${relativeTime(releaseAt, new Date())}`,
  };
}

function queuedCopy(voice: ReleaseVoice, status: ReleaseStatus): ReleaseCopy {
  // The counts are live here, not zero until SENT: a half-drained batch reads QUEUED with a
  // non-zero `sent` already, so once any row has landed this shows real progress rather than
  // repeating the generic "usually within a minute" estimate.
  const subline =
    status.sent > 0
      ? `Sending to ${memberCount(status.recipientCount)} · ${status.sent} sent so far.`
      : `Sending to ${memberCount(status.recipientCount)}, usually within a minute.`;
  return { statement: `The ${voice.noun} is queued.`, subline };
}

function sentCopy(
  voice: ReleaseVoice,
  releaseAt: string | null,
  status: ReleaseStatus,
  latestReleaseAt: string | undefined,
): ReleaseCopy {
  const sent = sentAt(releaseAt, latestReleaseAt, new Date());
  const statement =
    sent === null
      ? `The ${voice.noun} was sent.`
      : `The ${voice.noun} went out ${formatReleaseInstant(sent)}.`;
  if (status.sent === 0) {
    // SENT legitimately stays reachable when every row was suppressed - `sent` can be 0 here.
    // This must never read as a success: no delivered-count claim, no checkmark tone of voice.
    // "Anyone else" (not "the rest") stays true even when couldNotReach accounts for every
    // recipient and there is no remainder - the backend deliberately never discloses the
    // suppressed count, so this can only speak in terms that hold with zero opt-outs too.
    const subline =
      status.couldNotReach > 0
        ? `Nobody received it. ${status.couldNotReach} could not be reached, and anyone else had opted out.`
        : "Nobody received it. Every member had opted out.";
    return { statement, subline };
  }
  const subline =
    status.couldNotReach > 0
      ? `Delivered to ${memberCount(status.sent)} · ${status.couldNotReach} could not be reached.`
      : `Delivered to ${memberCount(status.sent)}.`;
  return { statement, subline };
}

/** `latestReleaseAt` is the latest instant this message type's release may honestly sit at,
 *  and it is what lets the two states above notice that the stored intent and the derived
 *  state disagree. Omitted for a message type with no such bound, which is the wrap-up. */
function releaseCopy(
  voice: ReleaseVoice,
  releaseAt: string | null,
  status: ReleaseStatus,
  latestReleaseAt?: string,
): ReleaseCopy {
  switch (status.state) {
    case "OFF":
      return offCopy(voice, status);
    case "SCHEDULED":
      return scheduledCopy(voice, releaseAt, status, latestReleaseAt);
    case "QUEUED":
      return queuedCopy(voice, status);
    case "SENT":
      return sentCopy(voice, releaseAt, status, latestReleaseAt);
  }
}

/** The card's two lines of text for the session's current wrap-up state - the honest fact,
 *  never a standing invitation to act. Callers must not call this with `session.wrapUpStatus
 *  === null`: that means "not the detail read", not a state to render copy for. */
export function wrapUpCopy(
  session: Session,
  status: ReleaseStatus,
): ReleaseCopy {
  return releaseCopy(WRAP_UP, session.wrapUpReleaseAt, status);
}

/** The same two lines for the session's reminder, read off its own stored release intent and
 *  bounded by the session's own start, which is what lets this one report a
 *  stored intent a reschedule has left behind. Same rule about a null status: it means "not
 *  the detail read", never a state. */
export function upcomingCopy(
  session: Session,
  status: ReleaseStatus,
): ReleaseCopy {
  return releaseCopy(
    REMINDER,
    session.upcomingReleaseAt,
    status,
    session.scheduledAt,
  );
}
