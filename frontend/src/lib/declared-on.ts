const DAY = new Intl.DateTimeFormat("en-GB", {
  day: "numeric",
  month: "short",
  year: "numeric",
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

/** "Sep 2026", the month of the instant in UTC. */
export function declaredMonth(iso: string): string {
  return withThreeLetterMonth(MONTH_YEAR_UTC, iso);
}

/** "Sep" in the current year and "Sep 2025" before it, the month of the instant in UTC. */
export function monthName(iso: string, now: Date): string {
  const sameYear = new Date(iso).getUTCFullYear() === now.getUTCFullYear();
  return withThreeLetterMonth(sameYear ? MONTH_UTC : MONTH_YEAR_UTC, iso);
}
