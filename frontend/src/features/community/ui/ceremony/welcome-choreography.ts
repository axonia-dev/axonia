import {
  type Cue,
  type CueStyle,
  fadeIn,
  part,
  popIn,
  rise,
  sheen,
} from "@/components/patterns/ceremony/cues";
import {
  drawLightBar,
  drawSparks,
  type Painter,
  viewportField,
} from "@/components/patterns/ceremony/paint";
import {
  createSeeds,
  type Point,
  sparkBudget,
  sparksAt,
  type SparkSeed,
} from "@/components/patterns/ceremony/spark-field";
import {
  type Beat,
  easeInOutCubic,
  easeOutBack,
  lerp,
  progress,
} from "@/components/patterns/ceremony/timeline";

export const WELCOME_LENGTH = 4.2;

export const WELCOME_BEATS = {
  slit: { start: 0, duration: 0.3 },
  doors: { start: 0.35, duration: 1.2 },
  stream: { start: 0.3, duration: 1.7 },
  badge: { start: 1.3, duration: 0.45 },
  rise: { start: 1.5, duration: 0.6 },
  gather: { start: 1.55, duration: 0.95 },
  avatarIn: { start: 2.15, duration: 0.35 },
  fly: { start: 2.5, duration: 0.85 },
  shift: { start: 2.65, duration: 0.6 },
  land: { start: 3.3, duration: 0.1 },
  count: { start: 3.25, duration: 0 },
  sheen: { start: 3.45, duration: 0.75 },
} satisfies Record<string, Beat>;

/** Where the newcomer's avatar is born, as a share of the card's height from its top. */
export const FLIER_ANCHOR = 0.32;
const FLIER_START_SIZE = 64;
const FLIER_END_SIZE = 28;
const ARC_LIFT = 110;
const FACE_STEP = 20;

function doors(p: number): CueStyle {
  const side = (50 * (1 - easeInOutCubic(p))).toFixed(2);
  return { "clip-path": `inset(0 ${side}% 0 ${side}%)` };
}

function shift(p: number): CueStyle {
  return {
    transform: `translateX(${(-(1 - easeInOutCubic(p)) * FACE_STEP).toFixed(2)}px)`,
  };
}

const FACEPILE = `${part("facepile")} [data-facepile-item]`;

export function welcomeCues(): Cue[] {
  return [
    { selector: part("surface"), beat: WELCOME_BEATS.doors, style: doors },
    { selector: part("badge"), beat: WELCOME_BEATS.badge, style: popIn },
    ...["meta", "name", "description"].map((name) => ({
      selector: part(name),
      beat: WELCOME_BEATS.rise,
      style: rise(24),
    })),
    { selector: part("footer"), beat: WELCOME_BEATS.rise, style: fadeIn },
    {
      selector: `${FACEPILE}:not(:first-child)`,
      beat: WELCOME_BEATS.shift,
      style: shift,
    },
    {
      selector: `${FACEPILE}:first-child`,
      beat: WELCOME_BEATS.land,
      style: fadeIn,
    },
    { selector: part("name"), beat: WELCOME_BEATS.sheen, style: sheen },
  ];
}

export interface FlierPose {
  readonly x: number;
  readonly y: number;
  readonly size: number;
  readonly opacity: number;
}

/** The newcomer's avatar: born above the scene, then arcing down onto its face in the facepile. */
export function flierPose(t: number, from: Point, to: Point): FlierPose {
  const born = progress(WELCOME_BEATS.avatarIn, t);
  const k = easeInOutCubic(progress(WELCOME_BEATS.fly, t));
  const landed = t >= WELCOME_BEATS.fly.start + WELCOME_BEATS.fly.duration;
  return {
    x: lerp(from.x, to.x, k),
    y: lerp(from.y, to.y, k) - Math.sin(k * Math.PI) * ARC_LIFT,
    size: lerp(FLIER_START_SIZE, FLIER_END_SIZE, k) * easeOutBack(born),
    opacity: landed ? 0 : Math.min(1, born * 2),
  };
}

function centreWithin(element: HTMLElement, card: HTMLElement): Point {
  const cardBox = card.getBoundingClientRect();
  const box = element.getBoundingClientRect();
  const scale = cardBox.width / (card.offsetWidth || cardBox.width);
  return {
    x: (box.left + box.width / 2 - cardBox.left) / scale,
    y: (box.top + box.height / 2 - cardBox.top) / scale,
  };
}

export function placeFlier(card: HTMLElement, t: number): void {
  const flier = card.querySelector<HTMLElement>("[data-ceremony-flier]");
  const face = card.querySelector<HTMLElement>(`${FACEPILE}:first-child`);
  if (flier === null || face === null) {
    return;
  }
  const from = { x: card.offsetWidth / 2, y: card.offsetHeight * FLIER_ANCHOR };
  const pose = flierPose(t, from, centreWithin(face, card));
  flier.style.width = `${pose.size}px`;
  flier.style.height = `${pose.size}px`;
  flier.style.transform = `translate(${pose.x - pose.size / 2}px, ${pose.y - pose.size / 2}px)`;
  flier.style.opacity = String(pose.opacity);
}

/** Light at the doors' edges, sparks streaming out through the gap, then gathering into the avatar. */
export function welcomePainter(hue: string): Painter {
  let seeds: readonly SparkSeed[] | null = null;
  return (context, t, card) => {
    const field = viewportField();
    seeds ??= createSeeds(
      sparkBudget(field.width, field.height, window.devicePixelRatio || 1),
      11,
    );
    const frame = card.getBoundingClientRect();
    const centre = {
      x: frame.left + frame.width / 2,
      y: frame.top + frame.height / 2,
    };
    const open = easeInOutCubic(progress(WELCOME_BEATS.doors, t));
    const brightness = progress(WELCOME_BEATS.slit, t) * (1 - open);
    for (const direction of [-1, 1]) {
      const x = centre.x + direction * (2 + (open * frame.width) / 2);
      drawLightBar(
        context,
        { x, top: frame.top, height: frame.height },
        hue,
        brightness,
      );
    }
    const streaming = {
      kind: "stream" as const,
      origin: centre,
      height: frame.height,
      beat: WELCOME_BEATS.stream,
    };
    drawSparks(context, sparksAt(streaming, seeds, t, field), hue);
    const gathering = {
      kind: "gather" as const,
      target: { x: centre.x, y: frame.top + frame.height * FLIER_ANCHOR },
      beat: WELCOME_BEATS.gather,
    };
    drawSparks(
      context,
      sparksAt(
        gathering,
        seeds.slice(0, Math.ceil(seeds.length / 3)),
        t,
        field,
      ),
      hue,
    );
  };
}
