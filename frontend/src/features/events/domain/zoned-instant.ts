/**
 * A wall-clock date and time, as a form holds them. An empty `time` means no hour was chosen.
 *
 * One shape only, so the two ends of a round trip cannot drift apart: `date` is `YYYY-MM-DD`, and
 * `time` is `HH:MM` with no seconds, which is what the session API takes and what
 * {@link instantToZoned} emits. A producer that speaks another dialect - `TimePicker` emits
 * `HH:MM:SS` - normalises before it puts a value here, not after.
 */
export interface ZonedParts {
  date: string;
  time: string;
}

/**
 * A wall-clock date and time in a named zone, resolved to an instant without a date library.
 *
 * A single "guess UTC, correct by the offset the guess lands in" pass is only exact when the offset
 * at the guess equals the offset at the corrected instant, which fails across a whole
 * transition-width window on the day a zone's clock moves (up to the zone's own UTC offset, e.g.
 * eight wall-clock hours around an `America/Los_Angeles` transition). A second pass collapses that
 * window to a single unresolvable hour: the wall time a spring-forward transition skips entirely,
 * where any answer is a defensible "around then".
 */
export function zonedToInstant(
  { date, time }: ZonedParts,
  timeZone: string,
): Date | null {
  const guess = new Date(`${date}T00:00:00Z`);
  if (Number.isNaN(guess.getTime())) {
    return null;
  }
  const [hours, minutes] = splitTime(time);
  if (hours === null || minutes === null) {
    return null;
  }
  guess.setUTCHours(hours, minutes, 0, 0);
  const first = new Date(
    guess.getTime() - offsetMinutes(guess, timeZone) * 60_000,
  );
  return new Date(guess.getTime() - offsetMinutes(first, timeZone) * 60_000);
}

/** The inverse: an instant read as the wall-clock parts a form would show for it in that zone. */
export function instantToZoned(iso: string, timeZone: string): ZonedParts {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(new Date(iso));
  const at = (type: string) =>
    parts.find((p) => p.type === type)?.value ?? "00";
  // `hour12: false` reports midnight as 24 in several engines, which would put 24:00 in a field.
  const hour = at("hour") === "24" ? "00" : at("hour");
  return {
    date: `${at("year")}-${at("month")}-${at("day")}`,
    time: `${hour}:${at("minute")}`,
  };
}

function splitTime(time: string): [number | null, number | null] {
  if (time.trim() === "") {
    return [0, 0];
  }
  const [hours, minutes] = time.split(":").map(Number);
  if (
    hours === undefined ||
    minutes === undefined ||
    Number.isNaN(hours) ||
    Number.isNaN(minutes)
  ) {
    return [null, null];
  }
  return [hours, minutes];
}

/** How far a zone is from UTC at one instant, in minutes, east positive. */
export function offsetMinutes(at: Date, timeZone: string): number {
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone,
    timeZoneName: "longOffset",
  }).formatToParts(at);
  const name = parts.find((p) => p.type === "timeZoneName")?.value ?? "GMT";
  const match = /GMT([+-])(\d{2}):(\d{2})/.exec(name);
  if (match === null) {
    return 0;
  }
  const [, sign, hh, mm] = match;
  const magnitude = Number(hh) * 60 + Number(mm);
  return sign === "-" ? -magnitude : magnitude;
}
