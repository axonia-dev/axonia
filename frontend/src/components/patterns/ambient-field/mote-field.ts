type LightSize = "large" | "medium" | "small";

export interface Light {
  readonly position: number;
  readonly freshness: number;
  readonly size: LightSize;
  readonly blooms: boolean;
}

export interface Box {
  readonly width: number;
  readonly height: number;
}

export interface MoteSample {
  readonly x: number;
  readonly y: number;
  readonly radius: number;
  readonly alpha: number;
}

export interface Mote {
  readonly x: number;
  readonly y: number;
  readonly radius: number;
  readonly alpha: number;
  readonly speed: number;
  readonly phase: number;
  readonly blooms: boolean;
}

export const AMBIENT_MOTES = 22;

const RADIUS: Record<LightSize, number> = { large: 4, medium: 3, small: 2.2 };
const AMBIENT = { radius: 1.6, alpha: 0.2 } as const;
const JITTER = 0.24;
const EDGE = 0.02;
const SWAY = 0.008;

/** A small seeded generator, so a community's sky is the same on every visit. */
function generator(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let mixed = Math.imul(state ^ (state >>> 15), 1 | state);
    mixed = (mixed + Math.imul(mixed ^ (mixed >>> 7), 61 | mixed)) ^ mixed;
    return ((mixed ^ (mixed >>> 14)) >>> 0) / 4294967296;
  };
}

/** FNV-1a: a stable 32-bit seed for a piece of text. */
export function seedOf(text: string): number {
  let hash = 0x811c9dc5;
  for (const character of text) {
    hash = Math.imul(hash ^ (character.codePointAt(0) ?? 0), 0x01000193);
  }
  return hash >>> 0;
}

const clamp = (value: number) => Math.min(1 - EDGE, Math.max(EDGE, value));

function lightMote(light: Light, random: () => number): Mote {
  return {
    x: clamp(light.position + (random() - 0.5) * JITTER),
    y: random(),
    radius: RADIUS[light.size],
    alpha: 0.32 + 0.5 * light.freshness,
    speed: 5 + random() * 5,
    phase: random() * Math.PI * 2,
    blooms: light.blooms,
  };
}

function dustMote(random: () => number): Mote {
  return {
    x: random(),
    y: random(),
    radius: AMBIENT.radius,
    alpha: AMBIENT.alpha,
    speed: 3 + random() * 2,
    phase: random() * Math.PI * 2,
    blooms: false,
  };
}

export function buildMotes(
  field: Readonly<{ lights: readonly Light[]; ambient: number; seed: number }>,
): Mote[] {
  const random = generator(field.seed);
  const lights = field.lights.map((light) => lightMote(light, random));
  const dust = Array.from({ length: field.ambient }, () => dustMote(random));
  return [...lights, ...dust];
}

function bloomOf(mote: Mote, seconds: number): number {
  if (!mote.blooms) {
    return 0;
  }
  return Math.max(0, Math.sin(seconds * 0.45 + mote.phase)) ** 8;
}

export function moteAt(mote: Mote, seconds: number, box: Box): MoteSample {
  const travelled = mote.y * box.height - seconds * mote.speed;
  const y = ((travelled % box.height) + box.height) % box.height;
  const sway = Math.sin(seconds * 0.4 + mote.phase) * SWAY;
  const bloom = bloomOf(mote, seconds);
  return {
    x: Math.min(1, Math.max(0, mote.x + sway)) * box.width,
    y,
    radius: mote.radius * (1 + 1.4 * bloom),
    alpha: Math.min(1, mote.alpha + 0.35 * bloom),
  };
}
