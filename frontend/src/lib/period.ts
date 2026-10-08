/**
 * Period boundaries and human-relative time, computed from the client's clock.
 * "This month" / "this quarter" deliberately use the viewer's local calendar —
 * the pulse is a warm signal, not an analytics report.
 */

export function startOfMonthIso(now: Date): string {
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString();
}

export function startOfQuarterIso(now: Date): string {
  const quarterMonth = Math.floor(now.getMonth() / 3) * 3;
  return new Date(now.getFullYear(), quarterMonth, 1).toISOString();
}

const UNITS: ReadonlyArray<[Intl.RelativeTimeFormatUnit, number]> = [
  ["year", 1000 * 60 * 60 * 24 * 365],
  ["month", 1000 * 60 * 60 * 24 * 30],
  ["week", 1000 * 60 * 60 * 24 * 7],
  ["day", 1000 * 60 * 60 * 24],
  ["hour", 1000 * 60 * 60],
  ["minute", 1000 * 60],
];

/** The exact moment behind a relative one. The relative form is what a reader scans; this is
 *  what they check when the answer matters, so it belongs on the same element as a title. */
export function absoluteTime(iso: string): string {
  return new Date(iso).toLocaleString("en", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

export function relativeTime(iso: string, now: Date): string {
  const delta = new Date(iso).getTime() - now.getTime();
  const rtf = new Intl.RelativeTimeFormat("en", { numeric: "always" });
  for (const [unit, ms] of UNITS) {
    // Strictly-greater so an exact-boundary delta (e.g. 7 days) reads in the
    // finer unit ("in 7 days") rather than collapsing to "in 1 week".
    if (Math.abs(delta) > ms) {
      return rtf.format(Math.trunc(delta / ms), unit);
    }
  }
  return "just now";
}
