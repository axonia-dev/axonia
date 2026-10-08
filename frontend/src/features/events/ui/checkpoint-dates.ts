import { instantToZoned } from "@/features/events/domain/zoned-instant";
import { shortDayLabel } from "@/features/events/ui/schedule-dates";
import { viewerZone } from "@/lib/zones";

/**
 * The one reading of the calendar day a checkpoint session meets on.
 *
 * Same rule as `SessionCard.whenLabel`: the instant is read on the zone that governs the session,
 * and the resulting civil date is then spelled in UTC by `shortDayLabel`. Three call sites read
 * this date (the chip, the tab's group headers, the capture dialog's picker) and each had its own
 * answer, so a Paris session could be named Oct 3 and Oct 4 on one screen. A caller holding a
 * whole Session resolves the zone with `governingZone`; one holding only a checkpoint passes the
 * zone the server already resolved onto it. Null falls back to the viewer's, as it always did.
 */
export function checkpointDayLabel(
  scheduledAt: string,
  governingTimeZone: string | null = null,
): string {
  const zone = governingTimeZone ?? viewerZone();
  return shortDayLabel(instantToZoned(scheduledAt, zone).date);
}
