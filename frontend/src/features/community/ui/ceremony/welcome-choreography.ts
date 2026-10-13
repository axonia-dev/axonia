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
  canvasColour,
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
export const WELCOME_SETTLE = 0.8;

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
  land: { start: 3.25, duration: 0.1 },
  count: { start: 3.25, duration: 0 },
  sheen: { start: 3.45, duration: 0.75 },
  wash: { start: 1.55, duration: 0.9 },
} satisfies Record<string, Beat>;

/** How far above a card without a banner the newcomer's avatar is born, in card pixels. */
const FLIER_ABOVE = 56;
const FLIER_START_SIZE = 64;
const FLIER_END_SIZE = 28;
/** The facepile's monogram against its face: 10 px initials on a 28 px avatar. */
const FLIER_TEXT_RATIO = 10 / 28;
/** What the flier keeps clear of the viewport's top edge at the peak of its arc. */
const ARC_CLEARANCE = 32;
const ARC_LIFT = 110;
const FACE_STEP = 20;
const WASH_PERCENT = 35;

function doors(p: number): CueStyle {
  if (p >= 1) {
    return { "clip-path": "" };
  }
  const side = (50 * (1 - easeInOutCubic(p))).toFixed(2);
  return { "clip-path": `inset(0 ${side}% 0 ${side}%)` };
}

/** The newcomer's slot stays folded under the other faces until they shift over to make room. */
function makeRoom(p: number): CueStyle {
  return {
    "margin-right":
      p >= 1 ? "" : `${(-(1 - easeInOutCubic(p)) * FACE_STEP).toFixed(2)}px`,
  };
}

/** A card without a banner opens onto its accent, which then settles into the plain surface. */
function wash(p: number): CueStyle {
  if (p >= 1) {
    return { "background-image": "" };
  }
  const strength = (WASH_PERCENT * (1 - easeInOutCubic(p))).toFixed(1);
  return {
    "background-image": `linear-gradient(to bottom, color-mix(in srgb, var(--community-accent) ${strength}%, transparent) 0, transparent 70%)`,
  };
}

const FACEPILE = `${part("facepile")} [data-facepile-item]`;

export interface WelcomeCard {
  readonly pictured: boolean;
}

export function welcomeCues({ pictured }: WelcomeCard): Cue[] {
  const washes = pictured
    ? []
    : [{ selector: part("identity"), beat: WELCOME_BEATS.wash, style: wash }];
  return [
    { selector: part("surface"), beat: WELCOME_BEATS.doors, style: doors },
    ...washes,
    { selector: part("badge"), beat: WELCOME_BEATS.badge, style: popIn },
    ...["meta", "name", "description"].map((name) => ({
      selector: part(name),
      beat: WELCOME_BEATS.rise,
      style: rise(24),
    })),
    { selector: part("footer"), beat: WELCOME_BEATS.rise, style: fadeIn },
    {
      selector: `${FACEPILE}:first-child`,
      beat: WELCOME_BEATS.shift,
      style: makeRoom,
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
  /** How far along its flight, eased, from 0 to 1. */
  readonly travel: number;
}

/** The newcomer's avatar: born above the scene, then arcing down onto its face in the facepile. */
export function flierPose(
  t: number,
  from: Point,
  to: Point,
  lift = ARC_LIFT,
): FlierPose {
  const born = progress(WELCOME_BEATS.avatarIn, t);
  const k = easeInOutCubic(progress(WELCOME_BEATS.fly, t));
  const landed = t >= WELCOME_BEATS.fly.start + WELCOME_BEATS.fly.duration;
  return {
    x: lerp(from.x, to.x, k),
    y: lerp(from.y, to.y, k) - Math.sin(k * Math.PI) * lift,
    size: lerp(FLIER_START_SIZE, FLIER_END_SIZE, k) * easeOutBack(born),
    opacity: landed ? 0 : Math.min(1, born * 2),
    travel: k,
  };
}

function scaleOf(card: HTMLElement): number {
  const width = card.getBoundingClientRect().width;
  return width / (card.offsetWidth || width || 1);
}

function centreWithin(element: HTMLElement, card: HTMLElement): Point {
  const cardBox = card.getBoundingClientRect();
  const box = element.getBoundingClientRect();
  const scale = scaleOf(card);
  return {
    x: (box.left + box.width / 2 - cardBox.left) / scale,
    y: (box.top + box.height / 2 - cardBox.top) / scale,
  };
}

/** The room between the viewport's top edge and the card, in card pixels. */
function headroomOf(card: HTMLElement): number {
  return card.getBoundingClientRect().top / scaleOf(card);
}

/** Where the avatar is born and the sparks gather, in card pixels: over the banner, or above a card without one. */
export function flierAnchor(card: HTMLElement): Point {
  const banner = card.querySelector<HTMLElement>(part("banner"));
  if (banner !== null) {
    return centreWithin(banner, card);
  }
  return {
    x: card.offsetWidth / 2,
    y: Math.max(-FLIER_ABOVE, FLIER_START_SIZE / 2 + 8 - headroomOf(card)),
  };
}

/** The arc's lift, lowered where a full lift would carry the avatar off the top of the viewport. */
function liftWithin(from: Point, to: Point, headroom: number): number {
  const room = (from.y + to.y) / 2 + headroom - ARC_CLEARANCE;
  return Math.max(0, Math.min(ARC_LIFT, room));
}

/** The flier's ring starts white and ends as the face's own surface-coloured ring. */
function ringAt(travel: number): string {
  const white = ((1 - travel) * 100).toFixed(1);
  return `color-mix(in srgb, #ffffff ${white}%, var(--surface))`;
}

export function placeFlier(card: HTMLElement, t: number): void {
  const flier = card.querySelector<HTMLElement>("[data-ceremony-flier]");
  const face = card.querySelector<HTMLElement>(`${FACEPILE}:first-child`);
  if (flier === null || face === null) {
    return;
  }
  const from = flierAnchor(card);
  const to = centreWithin(face, card);
  const pose = flierPose(t, from, to, liftWithin(from, to, headroomOf(card)));
  flier.style.width = `${pose.size}px`;
  flier.style.height = `${pose.size}px`;
  flier.style.fontSize = `${(pose.size * FLIER_TEXT_RATIO).toFixed(2)}px`;
  flier.style.transform = `translate(${pose.x - pose.size / 2}px, ${pose.y - pose.size / 2}px)`;
  flier.style.opacity = String(pose.opacity);
  flier.style.setProperty("--flier-ring", ringAt(pose.travel));
}

/** Light at the doors' edges, sparks streaming out through the gap, then gathering into the avatar. */
export function welcomePainter(hue: string): Painter {
  let seeds: readonly SparkSeed[] | null = null;
  let colour: string | null = null;
  return (context, t, card) => {
    const field = viewportField();
    seeds ??= createSeeds(
      Math.round(
        sparkBudget(field.width, field.height, window.devicePixelRatio || 1) /
          2,
      ),
      11,
    );
    colour ??= canvasColour(hue, card);
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
        colour,
        brightness,
      );
    }
    const streaming = {
      kind: "stream" as const,
      origin: centre,
      height: frame.height,
      beat: WELCOME_BEATS.stream,
    };
    drawSparks(context, sparksAt(streaming, seeds, t, field), colour);
    const anchor = flierAnchor(card);
    const scale = scaleOf(card);
    const gathering = {
      kind: "gather" as const,
      target: {
        x: frame.left + anchor.x * scale,
        y: frame.top + anchor.y * scale,
      },
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
      colour,
    );
  };
}
