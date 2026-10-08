import type { ActionItem } from "@/features/events/domain/action-item";
import {
  sectionAccentColor,
  sectionLabel,
} from "@/features/events/domain/section";
import { type Session, sessionTitle } from "@/features/events/domain/session";
import { isOpen } from "@/features/events/domain/slot";

/** How many talks the marquee's strip draws before it starts counting the rest instead. */
export const STRIP_SIZE = 3;

/**
 * How recent a held session has to be for the marquee to render loud. Past this, a community that
 * has gone quiet would otherwise announce itself with the page's biggest, brightest object, which
 * tells a visitor the opposite of the truth.
 */
const FRESH_FOR_DAYS = 42;

const DAY = 24 * 60 * 60 * 1000;

/**
 * Whether a held session is too far back for the marquee to render loud.
 *
 * Shared with the container rather than read off the recap: the surface is chosen before the
 * recap's own body is mounted, and a shell that renders loud and then quietens itself once its
 * child reports back is a flash of the wrong answer.
 */
export function isStale(heldAt: string, now: Date): boolean {
  return (now.getTime() - Date.parse(heldAt)) / DAY > FRESH_FOR_DAYS;
}

export interface RecapTalk {
  key: number;
  title: string;
  sectionTypeId: number;
  presenterUserIds: string[];
  durationMinutes: number;
  sectionLabel: string;
  icon: string | null;
  accentColor: string;
}

export interface SessionRecap {
  sessionId: number;
  title: string;
  heldAt: string;
  attendeeCount: number;
  /** True once the session is older than the freshness window: the marquee drops its gradient. */
  stale: boolean;
  /** Every talk the session held, in running order. */
  talks: RecapTalk[];
  /** The talks the strip draws: at most `STRIP_SIZE`, minus the one the headline already names. */
  strip: RecapTalk[];
  /** Talks past the strip, so the last tile can count them rather than drop them. */
  moreTalks: number;
  presenterUserIds: string[];
  totalMinutes: number;
  recordingUrl: string | null;
  notes: string | null;
  resourceCount: number;
  openActionPoints: number;
  /** An open action point assigned to the reader, which the marquee states ahead of the count. */
  mine: ActionItem | null;
}

export interface NextUp {
  sessionId: number;
  title: string;
  scheduledAt: string;
  booked: number;
  openSpots: number;
  /** A session nobody has given an agenda yet: open to anything rather than fully booked. */
  empty: boolean;
}

export interface RecapInput {
  session: Session;
  /** The session's own action points, read by `originSessionId`. Filtered to open server-side;
   *  filtered again here so a caller that widens the read cannot quietly inflate the tally. */
  actionPoints: readonly ActionItem[];
  resourceCount: number;
  meId: string | null;
  now: Date;
}

function talksOf(session: Session): RecapTalk[] {
  return session.sections.flatMap((section) =>
    section.slots.map((slot) => ({
      key: slot.id,
      title: slot.title ?? "Untitled talk",
      sectionTypeId: section.sectionTypeId ?? 0,
      presenterUserIds: slot.presenterUserIds,
      durationMinutes: slot.durationMinutes,
      sectionLabel: sectionLabel(section),
      icon: section.sectionTypeIcon,
      accentColor: sectionAccentColor(section),
    })),
  );
}

/**
 * The talks the strip has left to say, given a headline that already named one of them.
 *
 * `sessionTitle` names a session after its first titled talk, so on a surface that also lists the
 * talks the headline is one of the tiles. Printing it twice reads as a duplicate rather than as an
 * agenda. A session titled in its own right names no talk, and keeps all of them.
 */
function unsaidOf(talks: readonly RecapTalk[], headline: string): RecapTalk[] {
  const named = talks.findIndex((talk) => talk.title === headline);
  return talks.filter((_, index) => index !== named);
}

function presentersOf(talks: readonly RecapTalk[]): string[] {
  return [...new Set(talks.flatMap((talk) => talk.presenterUserIds))];
}

function minutesOf(session: Session, talks: readonly RecapTalk[]): number {
  if (session.durationMinutes !== null) {
    return session.durationMinutes;
  }
  return talks.reduce((sum, talk) => sum + talk.durationMinutes, 0);
}

function isStillOpen(item: ActionItem): boolean {
  return item.outcome === "OPEN";
}

function mineAmong(
  actionPoints: readonly ActionItem[],
  meId: string | null,
): ActionItem | null {
  if (meId === null) {
    return null;
  }
  return (
    actionPoints.find(
      (item) => isStillOpen(item) && item.assigneeUserId === meId,
    ) ?? null
  );
}

/**
 * What the community's last held session amounts to on the overview: its talks, who gave them,
 * what it left behind, and whether it is recent enough to still be worth shouting about.
 *
 * Takes the session already narrowed to a held one: deciding that is the read's job, not this
 * one's. Whether there is a session at all is the container's question, so this one always
 * describes a real session rather than carrying an absence its every field would then have to
 * apologise for.
 */
export function recapOf({
  session,
  actionPoints,
  resourceCount,
  meId,
  now,
}: RecapInput): SessionRecap {
  const talks = talksOf(session);
  const title = sessionTitle(session);
  const unsaid = unsaidOf(talks, title);
  const strip = unsaid.slice(0, STRIP_SIZE);
  return {
    sessionId: session.id,
    title,
    heldAt: session.scheduledAt,
    attendeeCount: session.attendeeCount,
    stale: isStale(session.scheduledAt, now),
    talks,
    strip,
    moreTalks: unsaid.length - strip.length,
    presenterUserIds: presentersOf(talks),
    totalMinutes: minutesOf(session, talks),
    recordingUrl: session.recordingUrl,
    notes: session.notes,
    resourceCount,
    openActionPoints: actionPoints.filter(isStillOpen).length,
    mine: mineAmong(actionPoints, meId),
  };
}

/**
 * What follows the last session, as the marquee's foot states it. The open-spot count is the point:
 * it is the one thing on the overview a reader can act on immediately.
 */
export function nextUpOf(session: Session | null): NextUp | null {
  if (session === null) {
    return null;
  }
  const slots = session.sections.flatMap((section) => section.slots);
  return {
    sessionId: session.id,
    title: sessionTitle(session),
    scheduledAt: session.scheduledAt,
    booked: slots.filter((slot) => !isOpen(slot)).length,
    openSpots: slots.filter(isOpen).length,
    empty: slots.length === 0,
  };
}
