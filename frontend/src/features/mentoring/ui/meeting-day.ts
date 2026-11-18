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

/** "Thu 19 Nov". */
export function meetingDay(day: string): string {
  return `${weekdayOf(day)} ${dayOfMonth(day)} ${monthOf(day)}`;
}

const LONG_WEEKDAY = new Intl.DateTimeFormat("en-GB", {
  weekday: "long",
  timeZone: "UTC",
});

const LONG_MONTH = new Intl.DateTimeFormat("en-GB", {
  month: "long",
  timeZone: "UTC",
});

/** "Thursday" for "2026-11-19". */
export function longWeekdayOf(day: string): string {
  return LONG_WEEKDAY.format(new Date(day));
}

/** "Thursday 19 November". */
export function longDay(day: string): string {
  return `${longWeekdayOf(day)} ${dayOfMonth(day)} ${LONG_MONTH.format(new Date(day))}`;
}
