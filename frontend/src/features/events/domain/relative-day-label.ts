const DAY_MS = 86_400_000;

function startOfLocalDay(date: Date): number {
  return new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
  ).getTime();
}

/**
 * Calendar-day distance from `now` to `target` in the viewer's timezone,
 * phrased for a countdown chip. `soon` marks today/tomorrow for accenting.
 */
export function relativeDayLabel(
  now: Date,
  target: Date,
): { label: string; soon: boolean } {
  const days = Math.round(
    (startOfLocalDay(target) - startOfLocalDay(now)) / DAY_MS,
  );
  if (days <= 0) return { label: "today", soon: true };
  if (days === 1) return { label: "tomorrow", soon: true };
  if (days < 14) return { label: `in ${days} days`, soon: false };
  return { label: `in ${Math.floor(days / 7)} weeks`, soon: false };
}
