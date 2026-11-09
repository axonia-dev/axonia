const DAY_MS = 86_400_000;
const FALLBACK_WINDOW_DAYS = 90;

/**
 * Read from the snapshot rather than from the window control, so everything drawn describes the
 * window the data came from, including while a refetch is still in flight.
 */
export function windowDaysOf(window: string): number {
  const days = Number.parseInt(window, 10);
  return Number.isNaN(days) ? FALLBACK_WINDOW_DAYS : days;
}

export interface Span {
  startMs: number;
  endMs: number;
}

export function windowSpan(window: string, generatedAt: string): Span {
  const endMs = Date.parse(generatedAt);
  return { startMs: endMs - windowDaysOf(window) * DAY_MS, endMs };
}

/** The window split into equal buckets, oldest first, the way the backend counts its series. */
export function bucketSpans(span: Span, count: number): Span[] {
  const width = (span.endMs - span.startMs) / count;
  return Array.from({ length: count }, (_, index) => ({
    startMs: span.startMs + index * width,
    endMs: span.startMs + (index + 1) * width,
  }));
}

/** Where an instant sits across the window, 0 at its start and 1 at its end. */
export function fractionOf(span: Span, iso: string): number {
  return (Date.parse(iso) - span.startMs) / (span.endMs - span.startMs);
}

const DAY_MONTH = new Intl.DateTimeFormat("en", {
  day: "numeric",
  month: "short",
});

/**
 * "4 Dec": the day and month an instant falls on in the reader's own zone, as the Schedule page
 * dates a session, day first as the rest of the page writes it.
 */
export function dayMonth(ms: number): string {
  const parts = DAY_MONTH.formatToParts(ms);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((candidate) => candidate.type === type)?.value ?? "";
  return `${part("day")} ${part("month")}`;
}

/** Whole days from one instant to a later one, rounded down. */
export function wholeDaysBetween(fromIso: string, toIso: string): number {
  return Math.floor((Date.parse(toIso) - Date.parse(fromIso)) / DAY_MS);
}

/**
 * Every day a bucket touches, from the day its first instant falls on to the day its last does.
 * Bucket edges fall at the snapshot's time of day, so neighbours can share the day an edge cuts.
 */
export function dayRange({ startMs, endMs }: Span): string {
  const first = dayMonth(startMs);
  const last = dayMonth(Math.max(startMs, endMs - 1));
  return first === last ? first : `${first} to ${last}`;
}
