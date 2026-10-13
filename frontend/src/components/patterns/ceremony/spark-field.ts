import {
  type Beat,
  easeInOutCubic,
  easeOutCubic,
  lerp,
  progress,
} from "./timeline";

export interface Point {
  readonly x: number;
  readonly y: number;
}

export interface Field {
  readonly width: number;
  readonly height: number;
}

export interface SparkSeed {
  readonly angle: number;
  readonly reach: number;
  readonly delay: number;
  readonly swirl: number;
  readonly size: number;
  readonly white: boolean;
}

export interface Spark {
  readonly x: number;
  readonly y: number;
  readonly alpha: number;
  readonly size: number;
  readonly white: boolean;
}

export type SparkMode =
  | {
      readonly kind: "condense";
      readonly targets: readonly Point[];
      readonly beat: Beat;
      readonly fadeOut: Beat;
    }
  | {
      readonly kind: "stream";
      readonly origin: Point;
      readonly height: number;
      readonly beat: Beat;
    }
  | { readonly kind: "gather"; readonly target: Point; readonly beat: Beat };

const MAX_DELAY = 0.45;

function seededRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let mixed = state;
    mixed = Math.imul(mixed ^ (mixed >>> 15), mixed | 1);
    mixed ^= mixed + Math.imul(mixed ^ (mixed >>> 7), mixed | 61);
    return ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296;
  };
}

/** `scale` sizes the sparks: 1 suits targets sampled 5 px apart. */
export function createSeeds(
  count: number,
  seed: number,
  scale = 1,
): SparkSeed[] {
  const random = seededRandom(seed);
  return Array.from({ length: count }, () => ({
    angle: random() * Math.PI * 2,
    reach: 0.55 + random() * 0.5,
    delay: random() * MAX_DELAY,
    swirl: (random() - 0.5) * 220,
    size: (1.4 + random() * 2.2) * scale,
    white: random() > 0.82,
  }));
}

/** Enough for a long name to read as letters on a phone. */
const BUDGET_FLOOR = 1100;
const BUDGET_CAP = 1800;

export function sparkBudget(
  width: number,
  height: number,
  pixelRatio: number,
): number {
  const byArea = Math.min(
    BUDGET_CAP,
    Math.max(BUDGET_FLOOR, (width * height) / 550),
  );
  return Math.round(byArea / Math.max(1, pixelRatio / 2.5));
}

/** Up to `count` of the points, picked in a seeded shuffle so any subset stays evenly spread. */
export function spreadTargets(
  points: readonly Point[],
  count: number,
  seed: number,
): Point[] {
  const random = seededRandom(seed);
  const keyed = points.map((point) => ({ point, key: random() }));
  keyed.sort((a, b) => a.key - b.key);
  return keyed.slice(0, count).map(({ point }) => point);
}

export interface FittedTargets {
  readonly targets: Point[];
  readonly step: number;
}

/**
 * Samples at `step`, and where that yields more points than the budget, samples again on a coarser
 * grid sized to fit it, so the letters thin out evenly instead of losing whole rows.
 */
export function fitTargets(
  sample: (step: number) => Point[],
  step: number,
  budget: number,
): FittedTargets {
  const fine = sample(step);
  if (fine.length <= budget) {
    return { targets: spreadTargets(fine, budget, 7), step };
  }
  const coarser = step * Math.sqrt(fine.length / budget);
  return { targets: spreadTargets(sample(coarser), budget, 7), step: coarser };
}

function staggered(beat: Beat, seed: SparkSeed): Beat {
  return {
    start: beat.start + seed.delay,
    duration: beat.duration - MAX_DELAY,
  };
}

function twinkle(t: number, index: number): number {
  return 0.6 + 0.4 * Math.sin(t * 11 + index);
}

function unitNormal(from: Point, to: Point): Point {
  const length = Math.hypot(to.x - from.x, to.y - from.y) || 1;
  return { x: -(to.y - from.y) / length, y: (to.x - from.x) / length };
}

function condense(
  mode: Extract<SparkMode, { kind: "condense" }>,
  seeds: readonly SparkSeed[],
  t: number,
  field: Field,
): Spark[] {
  const count = Math.min(seeds.length, mode.targets.length);
  const fade = 1 - progress(mode.fadeOut, t);
  if (count === 0 || fade <= 0) {
    return [];
  }
  const centre = { x: field.width / 2, y: field.height / 2 };
  const reach = Math.max(field.width, field.height);
  return seeds.slice(0, count).map((seed, index) => {
    const target =
      mode.targets[Math.floor((index * mode.targets.length) / count)] ?? centre;
    const start = {
      x: centre.x + Math.cos(seed.angle) * reach * seed.reach,
      y: centre.y + Math.sin(seed.angle) * reach * seed.reach,
    };
    const k = easeInOutCubic(progress(staggered(mode.beat, seed), t));
    const normal = unitNormal(start, target);
    const swirl = Math.sin(k * Math.PI) * seed.swirl;
    const landed = k > 0.97;
    return {
      x: lerp(start.x, target.x, k) + normal.x * swirl,
      y: lerp(start.y, target.y, k) + normal.y * swirl,
      alpha: fade * (landed ? 0.9 : 0.85 * twinkle(t, index)),
      size: seed.size * (1 + (1 - k) * 0.6),
      white: landed,
    };
  });
}

function stream(
  mode: Extract<SparkMode, { kind: "stream" }>,
  seeds: readonly SparkSeed[],
  t: number,
): Spark[] {
  return seeds.flatMap((seed, index) => {
    const k = progress(staggered(mode.beat, seed), t);
    if (k <= 0 || k >= 1) {
      return [];
    }
    const direction = index % 2 === 0 ? 1 : -1;
    return [
      {
        x:
          mode.origin.x +
          direction * easeOutCubic(k) * (200 + seed.reach * 300),
        y: mode.origin.y + (seed.angle / Math.PI - 1) * mode.height * 0.5,
        alpha: (1 - k) * 0.9 * twinkle(t, index),
        size: seed.size,
        white: seed.white,
      },
    ];
  });
}

function gather(
  mode: Extract<SparkMode, { kind: "gather" }>,
  seeds: readonly SparkSeed[],
  t: number,
  field: Field,
): Spark[] {
  const reach = Math.max(field.width, field.height) * 0.4;
  return seeds.flatMap((seed, index) => {
    const k = easeInOutCubic(progress(staggered(mode.beat, seed), t));
    if (k <= 0 || k >= 1) {
      return [];
    }
    const distance = (1 - k) * reach * seed.reach;
    const swirl = Math.sin(k * Math.PI) * seed.swirl * 0.5;
    return [
      {
        x:
          mode.target.x +
          Math.cos(seed.angle) * distance -
          Math.sin(seed.angle) * swirl,
        y:
          mode.target.y +
          Math.sin(seed.angle) * distance +
          Math.cos(seed.angle) * swirl,
        alpha: 0.9 * twinkle(t, index),
        size: seed.size,
        white: seed.white,
      },
    ];
  });
}

export function sparksAt(
  mode: SparkMode,
  seeds: readonly SparkSeed[],
  t: number,
  field: Field,
): Spark[] {
  switch (mode.kind) {
    case "condense":
      return condense(mode, seeds, t, field);
    case "stream":
      return stream(mode, seeds, t);
    case "gather":
      return gather(mode, seeds, t, field);
  }
}

export interface PixelImage {
  readonly width: number;
  readonly height: number;
  readonly data: ArrayLike<number>;
}

/** The opaque pixels on a grid `step` pixels apart; a fractional step lands on the nearest pixel. */
export function sampleTargets(image: PixelImage, step: number): Point[] {
  const points: Point[] = [];
  for (let row = step / 2; row < image.height; row += step) {
    const y = Math.floor(row);
    for (let column = step / 2; column < image.width; column += step) {
      const x = Math.floor(column);
      if ((image.data[(y * image.width + x) * 4 + 3] ?? 0) > 128) {
        points.push({ x, y });
      }
    }
  }
  return points;
}
