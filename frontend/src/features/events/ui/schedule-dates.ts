/**
 * How every schedule view names a calendar day, so a menu, a header, a panel and a timeline origin
 * all spell the same day the same way. Read in UTC because an occurrence date is a calendar day: a
 * local reading renders the day before for every viewer west of UTC.
 */

/** The compact form a menu title or a timeline origin has room for: "Thu, Jul 2". */
export function shortDayLabel(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
}

/** The written-out form a heading has room for, in the reader's own locale. */
export function longDayLabel(iso: string, withYear = false): string {
  return new Intl.DateTimeFormat(undefined, {
    weekday: "long",
    day: "numeric",
    month: "long",
    ...(withYear ? { year: "numeric" } : {}),
    timeZone: "UTC",
  }).format(new Date(`${iso}T00:00:00Z`));
}
