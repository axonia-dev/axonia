import { zonedToInstant } from "@/features/events/domain/zoned-instant";
import { cityOf } from "@/lib/zones";

interface Input {
  startsOn: string;
  preferredTime: string;
  timeZone: string;
  viewerZone: string;
}

/**
 * The reading shown under the preferred-time row: the next occurrence resolved in the series' own
 * zone, and - only when the organizer sits somewhere else - the same moment on their clock.
 *
 * The zone-differs clause is the point. A London lead configuring a Zurich guild is the case the
 * product got silently wrong before a zone existed, and this is where they find out.
 */
export function describeNextOccurrence({
  startsOn,
  preferredTime,
  timeZone,
  viewerZone,
}: Readonly<Input>): string | null {
  if (startsOn.trim() === "" || preferredTime.trim() === "") {
    return null;
  }

  const instant = zonedToInstant(
    { date: startsOn, time: preferredTime },
    timeZone,
  );
  if (instant === null) {
    return null;
  }

  const head = new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone,
  }).format(instant);

  const base = `Next session ${head}`;
  if (viewerZone === timeZone) {
    return base;
  }

  const local = new Intl.DateTimeFormat("en-GB", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: viewerZone,
  }).format(instant);

  return `${base} · ${local} in ${cityOf(viewerZone)}`;
}
