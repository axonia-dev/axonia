const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export function formatLastActive(
  iso: string | null,
  now: Date = new Date(),
): { label: string; active: boolean } {
  if (iso === null) {
    return { label: "Never signed in", active: false };
  }
  const then = new Date(iso).getTime();
  const delta = now.getTime() - then;

  if (delta < 5 * MINUTE) {
    return { label: "Active now", active: true };
  }
  if (delta < HOUR) {
    return { label: `Active ${Math.floor(delta / MINUTE)}m ago`, active: true };
  }
  if (delta < DAY) {
    return { label: `Active ${Math.floor(delta / HOUR)}h ago`, active: true };
  }
  const label = new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
  }).format(then);
  return { label: `Last seen ${label}`, active: false };
}
