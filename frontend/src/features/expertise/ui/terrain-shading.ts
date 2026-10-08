import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";

/** A colour in sRGB, each channel 0 to 1, which is the space the hex tokens and the atlas are already written in. */
export interface Rgb {
  r: number;
  g: number;
  b: number;
}

/**
 * The height below which nothing has been practised deeply enough to be land. The heightfield saturates rather than
 * normalising against its own peak, so this is an absolute coastline: the same amount of practice draws the same
 * ground whatever else the community has been doing.
 */
export const SEA_LEVEL = 0.3;

/**
 * How much of its reach an area's own colour holds at full strength. Inside this share the ground IS the accent;
 * outside it the colour feathers away to nothing at the edge. A linear falloff from the very centre - which is what
 * shipped - leaves the average point of a region carrying about a fifth of its colour, and five neighbourhoods drawn
 * that way are five shades of one teal, which is exactly what a reader cannot read.
 */
const ACCENT_CORE = 0.55;

/**
 * Where on the climb from shore to summit the accent itself sits: below it the land is cast towards the palette's
 * shore, above it towards its summit, in the very same colour throughout. Early, so most of an area's ground is its
 * own colour.
 */
const CREST = 0.45;

/**
 * How far the shore is cast towards the palette's own shore colour. It is cast, not faded: a shore that ran back to
 * a neutral drew every small area as a smudge, because a small area never climbs far up the heightfield and so never
 * reached the part of the climb that carried any colour at all.
 */
const SHORE_CAST = 0.42;

/** How far a summit is carried towards the palette's summit. Enough to read as height, far too little to lose the
 *  hue: a busy area is near the top of a saturating heightfield almost everywhere. */
const SUMMIT_CAST = 0.32;

/** The reach of a lone area's accent, in layout pixels: with no neighbour to halve the distance to, it needs a number. */
const LONE_REACH = 160;

export type Channels = readonly [number, number, number];
type Stop = readonly [number, Channels];

/**
 * The colours the ground is painted from, each `#rrggbb`. They come from the theme rather than from here, because
 * the two themes want opposite things of one ramp - see `ground-palette.ts`.
 */
export interface GroundPalette {
  /** Open water, from the deepest to the shallowest: the ocean is not one flat field either. */
  sea: readonly [string, string, string];
  /** Land no area claims, from the shore up: ground that reads as ground without competing for attention. */
  unclaimed: readonly [string, string, string];
  /** What an area's colour is cast towards at its coast. */
  shore: string;
  /** What it is carried towards at its summit. */
  summit: string;
  /** Where a colour the reading is not about is set aside to. */
  muted: string;
  /** The dashed ring that marks people open to questions, which belongs to no neighbourhood. */
  ring: string;
  /** What a mark is drawn in: the glyph, then the rim behind it. One pair for the whole map - see `mark-ink.ts`. */
  glyph: readonly [string, string];
  /** What the one mark a reader chose is drawn in, keeping the rim the rest of them use: the community's own
   *  accent, which is the one colour here that comes from outside the map's ramp. */
  chosen: string;
}

/** How far a dimmed region falls back towards unclaimed ground, and how far towards the shore. Not all the way: a
 *  reader still has to see that the ground is there, only that it is not what the reading is about. */
const DIM_TOWARDS_SLATE = 0.85;
const DIM_TOWARDS_SHORE = 0.2;

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

export function mix(a: Channels, b: Channels, k: number): Channels {
  return [
    a[0] + (b[0] - a[0]) * k,
    a[1] + (b[1] - a[1]) * k,
    a[2] + (b[2] - a[2]) * k,
  ];
}

function ramp(stops: Stop[], fraction: number): Channels {
  const at = clamp01(fraction);
  for (let step = 0; step < stops.length - 1; step++) {
    const [from, below] = stops[step]!;
    const [to, above] = stops[step + 1]!;
    if (at <= to) return mix(below, above, (at - from) / (to - from));
  }
  return stops[stops.length - 1]![1];
}

function stops(palette: readonly [string, string, string]): Stop[] {
  return [
    [0, channelsOf(palette[0]) ?? [0, 0, 0]],
    [0.5, channelsOf(palette[1]) ?? [0, 0, 0]],
    [1, channelsOf(palette[2]) ?? [0, 0, 0]],
  ];
}

export function cast(hex: string): Channels {
  return channelsOf(hex) ?? [0, 0, 0];
}

/** `#rrggbb`, which is the only form the ring accent tokens are written in. Anything else is treated as no accent. */
function channelsOf(hex: string): Channels | null {
  if (!/^#[0-9a-f]{6}$/i.test(hex)) return null;
  const packed = Number.parseInt(hex.slice(1), 16);
  return [(packed >> 16) & 255, (packed >> 8) & 255, packed & 255];
}

export function toRgb([r, g, b]: Channels): Rgb {
  return { r: r / 255, g: g / 255, b: b / 255 };
}

export function toChannels({ r, g, b }: Rgb): Channels {
  return [r * 255, g * 255, b * 255];
}

/** The climb from the shore to the summit, in one area's own colour throughout: only its value changes. */
function accented(
  tint: Channels,
  fraction: number,
  palette: GroundPalette,
): Channels {
  return fraction <= CREST
    ? mix(mix(tint, cast(palette.shore), SHORE_CAST), tint, fraction / CREST)
    : mix(
        tint,
        mix(tint, cast(palette.summit), SUMMIT_CAST),
        (fraction - CREST) / (1 - CREST),
      );
}

function dimmed(
  colour: Channels,
  towards: Channels,
  palette: GroundPalette,
): Channels {
  return mix(
    mix(colour, towards, DIM_TOWARDS_SLATE),
    cast(palette.shore),
    DIM_TOWARDS_SHORE,
  );
}

/** One point of the ground, as the ramp needs to know it. */
export interface GroundPoint {
  height: number;
  /** The neighbourhood's accent as `#rrggbb`, or null where no area claims the point. */
  accent: string | null;
  /** 1 at an area's centre, 0 at the edge of its reach. */
  nearness: number;
  /** What a reading does to the ground it is not about. */
  dim?: boolean;
}

/**
 * What the ground is painted at one point of it.
 *
 * Height decides whether a point is water or land and how high the land stands; the neighbourhood's accent decides
 * what colour that land is. The accent is the hue and the height is the value, and not the other way round: a ramp
 * that climbed through violet and teal on its own left every region the same colour, because a community's land sits
 * near the top of a saturating heightfield nearly everywhere and the accent was a fifth of a wash over it. A reader
 * has to be able to answer "which neighbourhood is that" from the ground alone.
 *
 */
export function groundColourAt(
  { height, accent, nearness, dim = false }: GroundPoint,
  palette: GroundPalette,
): Rgb {
  if (height <= SEA_LEVEL) {
    const sea = ramp(stops(palette.sea), height / SEA_LEVEL);
    return toRgb(dim ? dimmed(sea, sea, palette) : sea);
  }

  const fraction = clamp01((height - SEA_LEVEL) / (1 - SEA_LEVEL));
  const slate = ramp(stops(palette.unclaimed), fraction);
  const tint = accent === null ? null : channelsOf(accent);
  if (tint === null) return toRgb(dim ? dimmed(slate, slate, palette) : slate);

  const land = mix(
    slate,
    accented(tint, fraction, palette),
    clamp01(clamp01(nearness) / ACCENT_CORE),
  );
  return toRgb(dim ? dimmed(land, slate, palette) : land);
}

/**
 * How far a dimmed mark or band is carried towards the palette's muted colour. A reading the map is not about must
 * still be visible - the reader has to see that there IS something there - so this sets it aside rather than
 * erasing it.
 */
export const ACCENT_DIM = 0.76;

/** An area's own colour, turned down to what a reading is not about. */
export function dimAccent(hex: string, palette: GroundPalette): Rgb {
  const muted = cast(palette.muted);
  const tint = channelsOf(hex);
  return toRgb(tint === null ? muted : mix(tint, muted, ACCENT_DIM));
}

/** An area, where it sits, whose colour it carries and how far that colour reaches. */
export interface RegionSeed {
  tag: string;
  x: number;
  y: number;
  slot: number;
  reach: number;
}

/**
 * The areas whose accent the ground carries. An area with no contribution is left out: it has no marks, so it has no
 * elevation either, and its region would be open water with a colour nothing put there.
 *
 * Reach is the distance to the nearest other area rather than a chosen radius, so regions tile at whatever scale the
 * force layout settled on and the picture does not change character between a twelve-topic guild and a hundred-topic
 * one.
 */
export function regionSeeds(
  topics: PlacedTopic[],
  marks: PlacedContribution[],
): RegionSeed[] {
  const groupOf = new Map(marks.map((mark) => [mark.tag, mark.slot]));
  const claimed = topics.filter((topic) => groupOf.has(topic.tag));

  return claimed.map((topic) => {
    let nearest = Number.POSITIVE_INFINITY;
    for (const other of claimed) {
      if (other.tag === topic.tag) continue;
      nearest = Math.min(
        nearest,
        Math.hypot(other.x - topic.x, other.y - topic.y),
      );
    }
    return {
      tag: topic.tag,
      x: topic.x,
      y: topic.y,
      slot: groupOf.get(topic.tag)!,
      reach: Number.isFinite(nearest) ? nearest : LONE_REACH,
    };
  });
}

export interface Region {
  tag: string;
  slot: number;
  nearness: number;
}

/** Which area claims a point of the plane, and how strongly. Null where the nearest one is out of its own reach. */
export function nearestRegion(
  seeds: RegionSeed[],
  x: number,
  y: number,
): Region | null {
  let best: RegionSeed | null = null;
  let bestDistance = Number.POSITIVE_INFINITY;
  for (const seed of seeds) {
    const distance = Math.hypot(seed.x - x, seed.y - y);
    if (distance < bestDistance) {
      best = seed;
      bestDistance = distance;
    }
  }

  if (best === null || bestDistance >= best.reach) return null;
  return {
    tag: best.tag,
    slot: best.slot,
    nearness: 1 - bestDistance / best.reach,
  };
}
