import type {
  LobbyItem,
  LobbyShelfKey,
} from "@/features/community/domain/lobby";
import { instantToZoned } from "@/features/events/domain/zoned-instant";

type LobbySession = NonNullable<LobbyItem["nextSession"]>;

const GROWTH_WORTH_SAYING = 3;

export const SHELF_LABELS: Record<LobbyShelfKey, string> = {
  JOINED: "Joined",
  THIS_WEEK: "Sessions this week",
  GROWING: "Growing fast",
  OTHERS: "Every other community",
  EXPLORE: "Explore",
};

export const NOTHING_SCHEDULED = "Nothing scheduled yet";

export const SESSION_THIS_WEEK = "Session this week";

/** The kind, and why the community is worth a look when there is a reason to say. */
export function lobbyEyebrow(item: LobbyItem): string {
  if (item.joinedLast30Days >= GROWTH_WORTH_SAYING) {
    return `${item.kind} · +${item.joinedLast30Days} joined in 30 days`;
  }
  if (item.nextSessionThisWeek) {
    return `${item.kind} · ${SESSION_THIS_WEEK}`;
  }
  return item.kind;
}

function dayLabel(at: Date, now: Date, inUtc: boolean): string {
  const year = inUtc ? at.getUTCFullYear() : at.getFullYear();
  return new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    ...(year === now.getFullYear() ? {} : { year: "numeric" }),
    ...(inUtc ? { timeZone: "UTC" } : {}),
  })
    .format(at)
    .replace(",", "");
}

/** The calendar day an untimed session falls on, as a UTC midnight to format without a zone. */
function seriesDay(session: LobbySession): Date {
  const { date } = instantToZoned(session.scheduledAt, session.zone);
  return new Date(`${date}T00:00:00Z`);
}

const TIME = new Intl.DateTimeFormat("en-GB", {
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

/**
 * "Wed 19 Nov · 11:00 · Kafka deep dive" in the reader's own zone; the year only when it is not
 * this one. An untimed session is midnight in its series' zone, so it names the day alone, read
 * on that zone's clock: the reader's clock or UTC would date it a day off on one side or the other.
 */
export function nextSessionLine(item: LobbyItem, now: Date): string {
  const session = item.nextSession;
  if (session === null) return NOTHING_SCHEDULED;
  const title = sessionTitle(session);
  const when = sessionWhen(session, now);
  return title === "" ? when : `${when} · ${title}`;
}

/** When a session falls, without its title: "Wed 19 Nov · 11:00", or the day alone when untimed. */
export function sessionWhen(session: LobbySession, now: Date): string {
  const at = new Date(session.scheduledAt);
  return session.hasTime
    ? `${dayLabel(at, now, false)} · ${TIME.format(at)}`
    : dayLabel(seriesDay(session), now, true);
}

/** A session's title, or nothing when it has none worth showing. */
function sessionTitle(session: { title: string | null }): string {
  return session.title?.trim() ?? "";
}

/** What an untitled session is called wherever its title stands alone. */
export function sessionName(session: { title: string | null }): string {
  return sessionTitle(session) || "A session";
}

/** The invitation that stands in for the billboard while an organization or hub has no community. */
export const CREATE_HERO = {
  label: "Start a community",
  eyebrow: "Be the first",
  title: "Start a community here",
  body: "A community is where a practice lives: its sessions, library, and expertise.",
  action: "Create a community",
  /** For a person who cannot create one, to whom the invitation would be an order they cannot follow. */
  absent: "No communities yet",
} as const;
