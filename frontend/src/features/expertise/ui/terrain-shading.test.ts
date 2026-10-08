import { describe, expect, it } from "vitest";

import {
  dimAccent,
  groundColourAt,
  type GroundPalette,
  SEA_LEVEL,
} from "@/features/expertise/ui/terrain-shading";

const TEAL = "#2bc4bf";

/** Two palettes running opposite ways, which is the whole point of the module taking one: on a dark page the sea is
 *  near-black and land climbs towards white, and on a light page both are the other way round. */
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
  sea: ["#9fbdd6", "#c3d9e8", "#dfeef5"],
  unclaimed: ["#ece4d8", "#c6bbab", "#8d8274"],
  shore: "#f4eee4",
  summit: "#2b2135",
  muted: "#a79ead",
  ring: "#6b4fb0",
  glyph: ["#fbf8f6", "#17121c"],
  chosen: "#de3b78",
};

/** Perceived lightness is enough to say "higher is paler" without pinning a stop to an exact triple. */
function lightness({ r, g, b }: { r: number; g: number; b: number }): number {
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

describe("groundColourAt", () => {
  it("paints everything below the coastline as water", () => {
    const shallow = groundColourAt(
      { height: SEA_LEVEL - 0.01, accent: null, nearness: 0 },
      DARK,
    );

    expect(shallow.b).toBeGreaterThan(shallow.r);
    expect(shallow.b).toBeGreaterThan(shallow.g);
  });

  it("lifts the water towards the coast, so the ocean is not one flat field", () => {
    expect(
      lightness(
        groundColourAt({ height: SEA_LEVEL, accent: null, nearness: 0 }, DARK),
      ),
    ).toBeGreaterThan(
      lightness(groundColourAt({ height: 0, accent: null, nearness: 0 }, DARK)),
    );
  });

  // This is the whole reading: under a single ground colour a heightfield is a flat blob, and the hills the
  // density built read as noise the light happened to catch.
  it("climbs steadily as the land rises", () => {
    const climb = [0.4, 0.6, 0.8, 1].map((height) =>
      lightness(
        groundColourAt({ height: height, accent: null, nearness: 0 }, DARK),
      ),
    );

    expect(climb).toStrictEqual([...climb].sort((a, b) => a - b));
    expect(climb.at(-1)).toBeGreaterThan(climb[0]! * 2);
  });

  /** The same climb the other way up. A light page cannot read height as a lift towards white - there is nothing
   *  above white to lift into - so the light theme darkens with height instead, and the module must follow the
   *  palette it was handed rather than a direction of its own. */
  it("climbs the way the palette climbs, not the way one theme does", () => {
    const climb = [0.4, 0.6, 0.8, 1].map((height) =>
      lightness(
        groundColourAt({ height: height, accent: null, nearness: 0 }, LIGHT),
      ),
    );

    expect(climb).toStrictEqual([...climb].sort((a, b) => b - a));
  });

  // The light theme's page is near-white; a near-black ocean on it reads as a hole cut in the paper rather than
  // as water.
  it("takes the water from the palette too", () => {
    expect(
      lightness(
        groundColourAt({ height: 0, accent: null, nearness: 0 }, LIGHT),
      ),
    ).toBeGreaterThan(
      lightness(groundColourAt({ height: 0, accent: null, nearness: 0 }, DARK)),
    );
  });

  /** An area is known by its colour on either page, so the accent has to survive both ends of the climb whichever
   *  way the palette runs. */
  it("keeps an area's own hue under either palette", () => {
    for (const palette of [DARK, LIGHT]) {
      const shore = groundColourAt(
        { height: SEA_LEVEL + 0.02, accent: TEAL, nearness: 1 },
        palette,
      );
      const summit = groundColourAt(
        { height: 1, accent: TEAL, nearness: 1 },
        palette,
      );

      expect(shore.g).toBeGreaterThan(shore.r);
      expect(summit.g).toBeGreaterThan(summit.r);
    }
  });

  // The coastline is what makes the picture a map: a smooth fade from water to land leaves nothing to read an
  // area's extent from.
  it("steps from water to land at the coastline rather than fading across it", () => {
    const water = groundColourAt(
      { height: SEA_LEVEL, accent: null, nearness: 0 },
      DARK,
    );
    const land = groundColourAt(
      { height: SEA_LEVEL + 0.001, accent: null, nearness: 0 },
      DARK,
    );

    expect(
      Math.hypot(land.r - water.r, land.g - water.g, land.b - water.b),
    ).toBeGreaterThan(0.2);
  });

  it("carries the neighbourhood's own accent into its land", () => {
    const plain = groundColourAt(
      { height: 0.5, accent: null, nearness: 1 },
      DARK,
    );
    const tinted = groundColourAt(
      { height: 0.5, accent: TEAL, nearness: 1 },
      DARK,
    );

    expect(tinted).not.toStrictEqual(plain);
    expect(tinted.g).toBeGreaterThan(plain.g);
  });

  it("fades that accent out towards the edge of the area's reach", () => {
    const middle = groundColourAt(
      { height: 0.5, accent: TEAL, nearness: 0.5 },
      DARK,
    );

    expect(middle.g).toBeLessThan(
      groundColourAt({ height: 0.5, accent: TEAL, nearness: 1 }, DARK).g,
    );
    expect(middle.g).toBeGreaterThan(
      groundColourAt({ height: 0.5, accent: TEAL, nearness: 0 }, DARK).g,
    );
  });

  // An accent belongs to a region of practice. Water is where nothing was done, and colouring it would say a
  // neighbourhood covers ground it has never touched.
  it("leaves the water between regions uncoloured", () => {
    expect(
      groundColourAt({ height: 0.1, accent: TEAL, nearness: 1 }, DARK),
    ).toStrictEqual(
      groundColourAt({ height: 0.1, accent: null, nearness: 0 }, DARK),
    );
  });

  it("ignores an accent the theme answered in a form it cannot read", () => {
    expect(
      groundColourAt(
        { height: 0.5, accent: "oklch(0.7 0.1 200)", nearness: 1 },
        DARK,
      ),
    ).toStrictEqual(
      groundColourAt({ height: 0.5, accent: null, nearness: 1 }, DARK),
    );
  });

  /** Height decides the value and the accent decides the hue, so a shore must still be recognisably its own colour:
   *  faded back to a neutral instead, every small area came out a smudge. */
  it("keeps a low-lying area's colour rather than fading it to the shore", () => {
    const shore = groundColourAt(
      { height: SEA_LEVEL + 0.02, accent: TEAL, nearness: 1 },
      DARK,
    );

    expect(shore.g).toBeGreaterThan(shore.r * 1.5);
    expect(shore.g).toBeGreaterThan(0.2);
  });

  // A reading the map is not about still has to be visible: the reader must see that there IS ground there.
  it("sets a region back without erasing it when the reading is not about it", () => {
    for (const palette of [DARK, LIGHT]) {
      const lit = groundColourAt(
        { height: 0.7, accent: TEAL, nearness: 1 },
        palette,
      );
      const dim = groundColourAt(
        { height: 0.7, accent: TEAL, nearness: 1, dim: true },
        palette,
      );

      expect(lightness(dim)).toBeGreaterThan(0);
      expect(dim.g - dim.r).toBeLessThan(lit.g - lit.r);
    }
  });

  it("stays inside the channel range at both ends", () => {
    for (const height of [-1, 0, SEA_LEVEL, 1, 4]) {
      const { r, g, b } = groundColourAt(
        { height: height, accent: TEAL, nearness: 1 },
        LIGHT,
      );
      for (const channel of [r, g, b]) {
        expect(channel).toBeGreaterThanOrEqual(0);
        expect(channel).toBeLessThanOrEqual(1);
      }
    }
  });
});

describe("dimAccent", () => {
  it("turns an accent down without losing it", () => {
    const dim = dimAccent(TEAL, DARK);

    expect(lightness(dim)).toBeLessThan(
      lightness({ r: 43 / 255, g: 196 / 255, b: 191 / 255 }),
    );
    expect(dim.g).toBeGreaterThan(0.2);
  });

  it("sets an accent aside towards the palette's own muted colour", () => {
    expect(lightness(dimAccent(TEAL, LIGHT))).toBeGreaterThan(
      lightness(dimAccent(TEAL, DARK)),
    );
  });

  it("answers a colour the theme gave in a form it cannot read", () => {
    const { r, g, b } = dimAccent("oklch(0.7 0.1 200)", DARK);

    for (const channel of [r, g, b]) {
      expect(channel).toBeGreaterThan(0);
      expect(channel).toBeLessThan(1);
    }
  });
});
