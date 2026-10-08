import { describe, expect, it } from "vitest";

import { chosenInk, markInk, setBack } from "@/features/expertise/ui/mark-ink";
import {
  cast,
  type GroundPalette,
  toRgb,
} from "@/features/expertise/ui/terrain-shading";

/** The two themes run the same ramp in opposite directions, so which way round the pair goes is the theme's. */
const DARK: GroundPalette = {
  sea: ["#121b3c", "#1b2b60", "#263e84"],
  unclaimed: ["#221a3a", "#484e68", "#969cb0"],
  shore: "#221a3a",
  summit: "#ffffff",
  muted: "#605a74",
  ring: "#9b7ce8",
  glyph: ["#f4f1fb", "#0b0812"],
  chosen: "#ff5c97",
};

const LIGHT: GroundPalette = {
  ...DARK,
  muted: "#a79ead",
  glyph: ["#fbf8f6", "#17121c"],
  chosen: "#de3b78",
};

function luminance({ r, g, b }: { r: number; g: number; b: number }): number {
  const channel = (value: number) =>
    value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

describe("markInk", () => {
  /**
   * Picked against the ground, the pair inverts between two areas standing on ground the reader cannot tell apart,
   * which is what "why is it inverting sometimes" was. One pair for the whole map, whatever it stands on.
   */
  it.each([
    ["dark", DARK],
    ["light", LIGHT],
  ])("answers the same for every mark on a %s page", (_, palette) => {
    expect(markInk(palette)).toStrictEqual(markInk(palette));
  });

  /** The rim is what makes a mark legible, so it has to be the opposite value rather than merely a second colour. */
  it.each([
    ["dark", DARK],
    ["light", LIGHT],
  ])("rims the glyph in the opposite value on a %s page", (_, palette) => {
    const { ink, halo } = markInk(palette);

    expect(Math.abs(luminance(ink) - luminance(halo))).toBeGreaterThan(0.5);
  });

  it("sets a mark the reading is not about towards the muted colour", () => {
    const { ink } = markInk(DARK);

    expect(setBack(ink, DARK)).not.toStrictEqual(ink);
  });
});

describe("chosenInk", () => {
  const VIOLET = "#6e3ceb";
  const SAND = "#ffe08a";

  it.each([
    ["dark", DARK],
    ["light", LIGHT],
  ])(
    "paints the chosen mark in the community's own accent on a %s page",
    (_, palette) => {
      const ink = chosenInk({ ...palette, chosen: VIOLET }).ink;

      expect(ink).toStrictEqual(toRgb(cast(VIOLET)));
      expect(ink).not.toStrictEqual(markInk(palette).ink);
    },
  );

  /**
   * The rim is the opposite VALUE of what it rims, and for this one mark that is decided against the accent rather
   * than copied from the theme's own pair. A community's accent is one hue for both themes: violet rimmed in the
   * dark theme's near-black, on the dark theme's near-black ocean, is a hole rather than the thing a reader just
   * clicked - which is what shipped and what a screenshot showed.
   */
  it.each([
    ["dark", DARK],
    ["light", LIGHT],
  ])("rims a dark accent in the light ink on a %s page", (_, palette) => {
    const light = palette.glyph.find(
      (hex) => luminance(toRgb(cast(hex))) > 0.5,
    );

    expect(chosenInk({ ...palette, chosen: VIOLET }).halo).toStrictEqual(
      toRgb(cast(light!)),
    );
  });

  it.each([
    ["dark", DARK],
    ["light", LIGHT],
  ])("rims a light accent in the dark ink on a %s page", (_, palette) => {
    const dark = palette.glyph.find((hex) => luminance(toRgb(cast(hex))) < 0.5);

    expect(chosenInk({ ...palette, chosen: SAND }).halo).toStrictEqual(
      toRgb(cast(dark!)),
    );
  });
});
