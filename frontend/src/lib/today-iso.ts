import { getLocalTimeZone, today } from "@internationalized/date";

/** Today's calendar date, in the viewer's own time zone, as an ISO `YYYY-MM-DD` string - the
 *  floor a DatePicker's `minValue` needs wherever a future-only date is required. */
export function todayIso(): string {
  return today(getLocalTimeZone()).toString();
}
