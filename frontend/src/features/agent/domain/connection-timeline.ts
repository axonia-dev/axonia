import { relativeTime } from "@/lib/period";

import type { AgentConnection } from "./agent-connection";
import { onDate } from "./token-timeline";

export interface ConnectionTimeline {
  connected: string;
  lastUsed: string;
  ends: string;
}

const MINUTE_MS = 60 * 1000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

const END_UNITS: ReadonlyArray<readonly [Intl.RelativeTimeFormatUnit, number]> =
  [
    ["year", 365 * DAY_MS],
    ["month", 30 * DAY_MS],
    ["week", 7 * DAY_MS],
    ["day", DAY_MS],
    ["hour", HOUR_MS],
    ["minute", MINUTE_MS],
  ];

/** Rounded to the nearest unit rather than floored like the shared relativeTime: a connection
 *  granted for 180 days is a few seconds short of it by the time it is listed, and floored it
 *  would read a whole month shorter than granted. */
function endsIn(iso: string, now: Date): string {
  const delta = new Date(iso).getTime() - now.getTime();
  const unit = END_UNITS.find(([, ms]) => Math.abs(delta) > ms);
  if (unit === undefined) return "just now";
  const [name, ms] = unit;
  const count = Math.sign(delta) * Math.round(Math.abs(delta) / ms);
  return new Intl.RelativeTimeFormat("en", { numeric: "always" }).format(
    count,
    name,
  );
}

export function connectionTimeline(
  connection: AgentConnection,
  now: Date,
): ConnectionTimeline {
  const lastUsedAt = connection.lastUsedAt;
  return {
    connected: `Connected ${onDate(connection.activatedAt)}`,
    lastUsed:
      lastUsedAt === null || lastUsedAt === undefined
        ? "Never used"
        : `Used ${relativeTime(lastUsedAt, now)}`,
    ends: `Ends ${endsIn(connection.endsAt, now)}`,
  };
}
