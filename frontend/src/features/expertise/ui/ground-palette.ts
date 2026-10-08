import type { GroundPalette } from "@/features/expertise/ui/terrain-shading";

/**
 * The ground's colours, in the order {@link GroundPalette} wants them. They are tokens rather than constants because
 * the two themes want opposite things of the same ramp: on a dark page the sea is near-black and a summit bleaches
 * towards white, and on a light page that reads as a hole cut in the paper - there the sea is pale, the coast is the
 * palest cast of an area's colour and a summit is its deepest.
 */
const TOKENS = [
  "--map-sea-deep",
  "--map-sea-mid",
  "--map-sea-shallow",
  "--map-land-low",
  "--map-land-mid",
  "--map-land-high",
  "--map-shore",
  "--map-summit",
  "--map-muted",
  "--map-ring",
  "--map-glyph",
  "--map-glyph-rim",
  // Not one of the map's own: a community publishes its accent as `--primary` on the root element for as long as
  // it is the page, and the mark a reader chose is drawn in the colour that community is already drawn in.
  "--primary",
] as const;

/** The dark theme's own values, substituted where the cascade has not painted yet or under a test with none at all. */
const UNTHEMED: Record<(typeof TOKENS)[number], string> = {
  "--map-sea-deep": "#121b3c",
  "--map-sea-mid": "#1b2b60",
  "--map-sea-shallow": "#263e84",
  "--map-land-low": "#221a3a",
  "--map-land-mid": "#484e68",
  "--map-land-high": "#969cb0",
  "--map-shore": "#221a3a",
  "--map-summit": "#ffffff",
  "--map-muted": "#605a74",
  "--map-ring": "#9b7ce8",
  "--map-glyph": "#f4f1fb",
  "--map-glyph-rim": "#0b0812",
  "--primary": "#ff5c97",
};

/** WebGL reads no cascade, so the whole palette is resolved against the live theme once per layer the renderer
 *  builds, never per vertex: a heightfield asks for a colour tens of thousands of times. */
export function groundPalette(): GroundPalette {
  const style = globalThis.getComputedStyle(document.documentElement);
  const read = (token: (typeof TOKENS)[number]) => {
    const painted = style.getPropertyValue(token).trim();
    return painted === "" ? UNTHEMED[token] : painted;
  };

  return {
    sea: [
      read("--map-sea-deep"),
      read("--map-sea-mid"),
      read("--map-sea-shallow"),
    ],
    unclaimed: [
      read("--map-land-low"),
      read("--map-land-mid"),
      read("--map-land-high"),
    ],
    shore: read("--map-shore"),
    summit: read("--map-summit"),
    muted: read("--map-muted"),
    ring: read("--map-ring"),
    glyph: [read("--map-glyph"), read("--map-glyph-rim")],
    chosen: read("--primary"),
  };
}
