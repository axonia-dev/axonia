import { easeOutCubic } from "@/components/patterns/ceremony/timeline";

/** The agreed echo, the prototype's variant C at 0.4x. Slow on purpose: the first answer to a question is an event. */
export const ECHO = {
  durationMs: 4250,
  ringLagsMs: [0, 450, 900],
  staggerMs: 275,
  featherShare: 0.45,
  localRingPx: 260,
  ringPeakAlpha: 0.55,
  twinkleMs: 1750,
  fadeMs: 250,
  panelCueMs: 2340,
  panelStepMs: 90,
} as const;

export interface Bounds {
  minX: number;
  minY: number;
  maxX: number;
  maxY: number;
}
export interface Drop {
  x: number;
  y: number;
  delayMs: number;
  slot: number;
}
export interface Wave extends Drop {
  reach: number;
  feather: number;
}
export interface Echo {
  waves: readonly Wave[];
  local: boolean;
}
export interface Ring {
  x: number;
  y: number;
  radius: number;
  alpha: number;
  slot: number;
}

const clamp01 = (value: number) => Math.min(1, Math.max(0, value));
const inverseEaseOutCubic = (y: number) => 1 - Math.cbrt(1 - y);

function farthestCorner({ x, y }: Drop, b: Bounds): number {
  return Math.max(
    Math.hypot(b.minX - x, b.minY - y),
    Math.hypot(b.maxX - x, b.minY - y),
    Math.hypot(b.minX - x, b.maxY - y),
    Math.hypot(b.maxX - x, b.maxY - y),
  );
}

/** A drop's front runs past the farthest corner by its own soft edge, so every point is fully taken when it ends. */
export function echoFor(drops: readonly Drop[], bounds: Bounds): Echo {
  const waves = drops.map((drop) => {
    const corner = farthestCorner(drop, bounds);
    const feather = corner * ECHO.featherShare;
    return { ...drop, reach: corner + feather, feather };
  });
  return { waves, local: waves.length > 1 };
}

function progressOf(wave: Wave, elapsedMs: number, lagMs = 0): number {
  return clamp01((elapsedMs - wave.delayMs - lagMs) / ECHO.durationMs);
}

/** How far one wave has carried a point. A finished wave has taken all of its reach, stated exactly: worked out, the
 *  far corner comes to a hair under 1, and a settled frame must be the new picture and nothing near it. */
function carried(wave: Wave, x: number, y: number, elapsedMs: number): number {
  const progress = progressOf(wave, elapsedMs);
  const distance = Math.hypot(x - wave.x, y - wave.y);
  if (progress === 1 && distance <= wave.reach) return 1;
  return clamp01(
    (easeOutCubic(progress) * wave.reach - distance) / wave.feather,
  );
}

/** How far the echo has carried a point into the new picture: 0 untouched, 1 settled. Whichever drop got there first. */
export function amountAt(
  echo: Echo,
  x: number,
  y: number,
  elapsedMs: number,
): number {
  let amount = 0;
  for (const wave of echo.waves) {
    amount = Math.max(amount, carried(wave, x, y, elapsedMs));
  }
  return amount;
}

/** When the first ring of the nearest drop reaches a point, which is when a mark there twinkles. */
export function reachedAt(echo: Echo, x: number, y: number): number {
  let soonest = Number.POSITIVE_INFINITY;
  for (const wave of echo.waves) {
    const share = Math.min(1, Math.hypot(x - wave.x, y - wave.y) / wave.reach);
    soonest = Math.min(
      soonest,
      wave.delayMs + ECHO.durationMs * inverseEaseOutCubic(share),
    );
  }
  return soonest;
}

/** The rings on screen at a time. With several drops each one fades out close to home: rain, not a dozen waves. */
export function ringsAt(echo: Echo, elapsedMs: number): Ring[] {
  const rings: Ring[] = [];
  for (const wave of echo.waves) {
    for (const lag of ECHO.ringLagsMs) {
      const t = progressOf(wave, elapsedMs, lag);
      if (t <= 0 || t >= 1) continue;
      const radius = easeOutCubic(t) * wave.reach;
      const local = echo.local ? Math.max(0, 1 - radius / ECHO.localRingPx) : 1;
      const alpha = ECHO.ringPeakAlpha * (1 - t) ** 0.8 * local;
      if (alpha > 0.01)
        rings.push({ x: wave.x, y: wave.y, radius, alpha, slot: wave.slot });
    }
  }
  return rings;
}

/** The twinkles outlast the last ring lag (900 ms), so one tail covers both. */
export function echoLength(echo: Echo): number {
  const lastDelay = Math.max(0, ...echo.waves.map((wave) => wave.delayMs));
  return lastDelay + ECHO.durationMs + ECHO.twinkleMs;
}

export function fadeAmount(elapsedMs: number): number {
  return easeOutCubic(clamp01(elapsedMs / ECHO.fadeMs));
}
