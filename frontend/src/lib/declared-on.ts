const DAY = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  year: "numeric",
});

const DAY_UTC = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "UTC",
});

const DAY_MONTH_UTC = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  timeZone: "UTC",
});

const MONTH_YEAR_UTC = new Intl.DateTimeFormat("en-GB", {
  month: "short",
  year: "numeric",
  timeZone: "UTC",
});

const MONTH_UTC = new Intl.DateTimeFormat("en-GB", {
  month: "short",
  timeZone: "UTC",
});

/** Recent ICU writes en-GB September as "Sept"; the other short months are three letters already. */
function withThreeLetterMonth(format: Intl.DateTimeFormat, iso: string) {
  return format
    .formatToParts(new Date(iso))
    .map((part) =>
      part.type === "month" ? part.value.slice(0, 3) : part.value,
    )
    .join("");
}

/** "12 Sep 2026", in the reader's own zone: `declaredAt` is an instant, not a calendar day. */
export function declaredOn(iso: string): string {
  return withThreeLetterMonth(DAY, iso);
}

/** "1 Sep 2026" from a calendar day such as "2026-09-01": a date with no zone, read as one. */
export function calendarDay(day: string): string {
  return withThreeLetterMonth(DAY_UTC, day);
}

/** "1 Sep" from a calendar day such as "2026-09-01", for a day close enough to need no year. */
export function dayAndMonth(day: string): string {
  return withThreeLetterMonth(DAY_MONTH_UTC, day);
}

/** "Sep 2026", the month of the instant in UTC. */
export function declaredMonth(iso: string): string {
  return withThreeLetterMonth(MONTH_YEAR_UTC, iso);
}

/** "Sep" in the viewer's current year and "Sep 2025" in any other, the month of the instant in UTC. */
export function monthName(iso: string, now: Date): string {
  const sameYear = new Date(iso).getUTCFullYear() === now.getFullYear();
  return withThreeLetterMonth(sameYear ? MONTH_UTC : MONTH_YEAR_UTC, iso);
}
