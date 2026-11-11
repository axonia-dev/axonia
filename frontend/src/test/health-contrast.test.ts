import { describe, expect, it } from "vitest";

import { accentVariables } from "@/features/community/ui/community-accent";
import { COMMUNITY_PALETTE } from "@/lib/kind-hue";
import {
  contrast,
  DARK,
  type Declarations,
  mix,
  resolve,
  type Rgb,
  ROOT,
} from "@/test/theme-tokens";

/** A Tailwind `bg-x/15` over the surface, read as plain alpha blending. */
function tint(colour: Rgb, surface: Rgb, share: number): Rgb {
  return mix(colour, surface, share);
}

const THEMES = ["light", "dark"] as const;

function tokensFor(theme: (typeof THEMES)[number], hue: string): Declarations {
  const accent = Object.entries(
    accentVariables({ accentColor: hue, kindId: 1 }),
  );
  return new Map([
    ...ROOT,
    ...(theme === "dark" ? DARK : []),
    ...(accent as [string, string][]),
  ]);
}

const CASES = THEMES.flatMap((theme) =>
  COMMUNITY_PALETTE.map((hue) => ({
    theme,
    hue,
    tokens: tokensFor(theme, hue),
  })),
);

describe("health data tones", () => {
  it.each(CASES)(
    "hold 3:1 on the surface for every mark in $theme $hue",
    ({ tokens }) => {
      const surface = resolve("var(--surface)", tokens);
      for (const token of ["--data-1", "--data-2", "--data-3"]) {
        expect(
          contrast(resolve(`var(${token})`, tokens), surface),
          token,
        ).toBeGreaterThanOrEqual(3);
      }
    },
  );
});

describe("health data series", () => {
  const talks = "var(--data-1)";
  const people = "var(--data-2)";
  const joins = "var(--data-3)";

  it.each(CASES)(
    "keep the talks and joins bars at least 1.7:1 apart in $theme $hue",
    ({ tokens }) => {
      expect(
        contrast(resolve(talks, tokens), resolve(joins, tokens)),
      ).toBeGreaterThanOrEqual(1.7);
    },
  );

  it.each(CASES)(
    "keep the people line at least 1.3:1 from both bars in $theme $hue",
    ({ tokens }) => {
      for (const bar of [talks, joins]) {
        expect(
          contrast(resolve(people, tokens), resolve(bar, tokens)),
          bar,
        ).toBeGreaterThanOrEqual(1.3);
      }
    },
  );
});

describe("health status pills", () => {
  const PILLS: ReadonlyArray<{
    level: string;
    text: string;
    ground: (t: Declarations) => Rgb;
  }> = [
    {
      level: "WORRY",
      text: "--danger-ink",
      ground: (t) =>
        tint(resolve("var(--danger)", t), resolve("var(--surface)", t), 0.15),
    },
    {
      level: "WATCH",
      text: "--warning-ink",
      ground: (t) =>
        tint(resolve("var(--warning)", t), resolve("var(--surface)", t), 0.15),
    },
    {
      level: "GOOD",
      text: "--success-ink",
      ground: (t) =>
        tint(resolve("var(--success)", t), resolve("var(--surface)", t), 0.15),
    },
    {
      level: "GREAT",
      text: "--success-foreground",
      ground: (t) => resolve("var(--great)", t),
    },
    {
      level: "QUIET",
      text: "--quiet-ink",
      ground: (t) => resolve("var(--surface-sunken)", t),
    },
  ];

  it.each(CASES)("hold 4.5:1 for their word in $theme $hue", ({ tokens }) => {
    for (const pill of PILLS) {
      expect(
        contrast(resolve(`var(${pill.text})`, tokens), pill.ground(tokens)),
        pill.level,
      ).toBeGreaterThanOrEqual(4.5);
    }
  });
});
