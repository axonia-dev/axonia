export const GLOW_DAYS = 21;

const DAY_MS = 86_400_000;

/** How strongly an upcoming card glows: 1 at the start, fading to 0 at GLOW_DAYS out. */
export function upcomingGlow(scheduledAt: string, now: Date): number {
  const ahead = new Date(scheduledAt).getTime() - now.getTime();
  return Math.min(1, Math.max(0, 1 - ahead / (GLOW_DAYS * DAY_MS)));
}
