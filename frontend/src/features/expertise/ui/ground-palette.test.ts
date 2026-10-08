import { afterEach, expect, it } from "vitest";

import { groundPalette } from "@/features/expertise/ui/ground-palette";

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
  // Not a map token: the community's own accent, which is what the chosen mark is painted in.
  "--primary",
];

afterEach(() => {
  for (const token of TOKENS) {
    document.documentElement.style.removeProperty(token);
  }
});

it("takes every colour from the theme the page is wearing", () => {
  for (const [index, token] of TOKENS.entries()) {
    document.documentElement.style.setProperty(
      token,
      `#${index.toString().repeat(6)}`,
    );
  }
  const palette = groundPalette();

  expect([
    ...palette.sea,
    ...palette.unclaimed,
    palette.shore,
    palette.summit,
    palette.muted,
    palette.ring,
    palette.chosen,
  ]).toStrictEqual(TOKENS.map((_, index) => `#${index.toString().repeat(6)}`));
});

/** WebGL runs before the cascade has painted on the very first frame, and under a unit test there is no cascade at
 *  all. A palette of empty strings would draw the whole ground black. */
it("answers a whole palette where the theme has painted nothing", () => {
  for (const hex of [
    ...groundPalette().sea,
    ...groundPalette().unclaimed,
    groundPalette().shore,
    groundPalette().summit,
    groundPalette().muted,
    groundPalette().ring,
    groundPalette().chosen,
  ]) {
    expect(hex).toMatch(/^#[0-9a-f]{6}$/);
  }
});

/**
 * The one colour on the drawing that is not the map's own ramp.
 *
 * A community publishes its accent as `--primary` on the root element for as long as it is the page
 * (`community-accent.ts`), so the mark a reader chose is drawn in the colour that community is already drawn in,
 * rather than in a pink the map keeps to itself.
 */
it("paints the chosen mark in the community's own accent", () => {
  document.documentElement.style.setProperty("--primary", "#abcdef");

  expect(groundPalette().chosen).toBe("#abcdef");
});
