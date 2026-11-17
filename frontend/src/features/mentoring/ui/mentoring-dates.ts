import { declaredOn } from "@/lib/declared-on";

/** "since 1 Dec 2026" while ongoing, "1 Dec 2026 to 9 Feb 2027" once ended, and one day when both fall on it. */
export function pairingDates(
  startedAt: string,
  endedAt: string | null,
): string {
  if (endedAt === null) return `since ${declaredOn(startedAt)}`;
  const start = declaredOn(startedAt);
  const end = declaredOn(endedAt);
  return start === end ? start : `${start} to ${end}`;
}
