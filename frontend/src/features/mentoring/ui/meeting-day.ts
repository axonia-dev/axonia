const WEEKDAY = new Intl.DateTimeFormat("en-GB", {
  weekday: "short",
  timeZone: "UTC",
});

const MONTH = new Intl.DateTimeFormat("en-GB", {
  month: "short",
  timeZone: "UTC",
});

/** "Thu" for a calendar day such as "2026-11-19", read as a day with no zone. */
export function weekdayOf(day: string): string {
  return WEEKDAY.format(new Date(day));
}

/** "Nov" for "2026-11-19"; ICU's "Sept" is cut to three letters like the rest. */
export function monthOf(day: string): string {
  return MONTH.format(new Date(day)).slice(0, 3);
}

/** "19" for "2026-11-19". */
export function dayOfMonth(day: string): string {
  return String(Number(day.slice(8, 10)));
}
