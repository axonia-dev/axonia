import { z } from "zod";

/**
 * A member's calendar subscription to one community.
 *
 * `url` is non-null only in the response that minted it: the token it carries is hashed and
 * never stored, so reading an existing subscription answers with the fetch stamp and no URL.
 * That is a state of the subscription, not a failure, and the dialog says so rather than
 * showing an empty field.
 */
export const calendarSubscriptionSchema = z.object({
  url: z.string().nullable(),
  lastFetchedAt: z.string().nullable(),
  lastFetchedAgent: z.string().nullable(),
});

export type CalendarSubscription = z.infer<typeof calendarSubscriptionSchema>;

interface CalendarTarget {
  id: string;
  label: string;
  href: string;
}

/** Calendar clients subscribe under `webcal://`, which is the feed's own https URL reskinned
 *  so the operating system hands it to a calendar instead of a browser. */
export function webcalUrl(url: string): string {
  return url.replace(/^https?:\/\//, "webcal://");
}

const GOOGLE_ADD = "https://calendar.google.com/calendar/r?cid=";
const OUTLOOK_ADD = "https://outlook.office.com/calendar/0/addfromweb";

/**
 * The one-click handovers, in the order the dialog offers them. `name` is what Outlook files
 * the subscribed calendar under; the other clients take the name from the feed itself.
 *
 * Named by vendor alone. "Calendar" was in every second label inside a dialog titled "Add to
 * calendar", where the one word that distinguishes the four is the vendor, and the brand mark
 * beside each says which application is meant faster than any word does.
 */
export function calendarTargets(
  webcal: string,
  name: string,
): CalendarTarget[] {
  const feed = encodeURIComponent(webcal);
  return [
    { id: "google", label: "Google", href: `${GOOGLE_ADD}${feed}` },
    {
      id: "outlook",
      label: "Outlook",
      href: `${OUTLOOK_ADD}?url=${feed}&name=${encodeURIComponent(name)}`,
    },
    { id: "apple", label: "Apple", href: webcal },
    { id: "other", label: "Any other", href: webcal },
  ];
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

function ago(count: number, unit: string): string {
  return `${count} ${unit}${count === 1 ? "" : "s"} ago`;
}

/** Elapsed time only, never a formatted date: a delta reads the same in every time zone,
 *  which keeps the line deterministic under the suite's pinned one. */
function elapsed(delta: number): string {
  if (delta < MINUTE) return "just now";
  if (delta < HOUR) return ago(Math.floor(delta / MINUTE), "minute");
  if (delta < DAY) return ago(Math.floor(delta / HOUR), "hour");
  return ago(Math.floor(delta / DAY), "day");
}

const KNOWN_AGENTS: ReadonlyArray<readonly [RegExp, string]> = [
  [/outlook/i, "Outlook"],
  [/google/i, "Google Calendar"],
  [/calendaragent|dataaccessd|macos|iphone|ipados/i, "Apple Calendar"],
  [/thunderbird/i, "Thunderbird"],
];

const AGENT_LIMIT = 40;

/** A stored user agent is a whole banner. The well-known clients get the name a member would
 *  recognise, and anything else is reduced to its product token, capped. */
function agentLabel(agent: string): string {
  const known = KNOWN_AGENTS.find(([pattern]) => pattern.test(agent));
  if (known !== undefined) return known[1];
  const token = agent.split(" ")[0] ?? agent;
  return token.length > AGENT_LIMIT ? `${token.slice(0, AGENT_LIMIT)}…` : token;
}

/**
 * The one line that proves an otherwise opaque subscription works. A URL pasted into the
 * wrong box never syncs, and nothing else anywhere would say so.
 *
 * Null until a sync has actually happened. Before then there is no evidence to report, and the
 * line said only that none had arrived - which is the state of every link in the minute after it
 * is minted, on a schedule the member does not control and cannot hurry. Waiting is not news.
 */
export function formatLastSync(
  { lastFetchedAt, lastFetchedAgent }: CalendarSubscription,
  now: Date,
): string | null {
  if (lastFetchedAt === null) return null;
  const when = elapsed(now.getTime() - new Date(lastFetchedAt).getTime());
  if (lastFetchedAgent === null) return `Last synced ${when}`;
  return `Last synced ${when}, by ${agentLabel(lastFetchedAgent)}`;
}
