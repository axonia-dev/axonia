/// <reference types="node" />
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

import { accentVariables } from "@/features/community/ui/community-accent";
import { COMMUNITY_PALETTE } from "@/lib/kind-hue";
import { SRC } from "@/test/source-files";

// jsdom resolves no custom property and computes no colour, so this reads the theme tokens out of
// globals.css and resolves them the way the cascade does on the root element: the theme block over
// :root, then the community accent the shell writes inline. Only the forms the health tokens use
// are understood (a hex, a var() and an srgb color-mix), and anything else fails loudly rather
// than measuring a guess.

type Rgb = [number, number, number];
type Declarations = Map<string, string>;

const CSS = readFileSync(join(SRC, "styles", "globals.css"), "utf8");

function block(selector: string): Declarations {
  const start = CSS.indexOf(`${selector} {`);
  const end = CSS.indexOf("\n}", start);
  const body = CSS.slice(start, end).replaceAll(/\/\*[\s\S]*?\*\//g, "");
  const declarations: Declarations = new Map();
  for (const statement of body.split(";")) {
    const at = statement.indexOf("--");
    const colon = statement.indexOf(":", at);
    if (at === -1 || colon === -1) continue;
    const value = statement
      .slice(colon + 1)
      .split(/\s+/)
      .join(" ")
      .trim();
    declarations.set(
      statement.slice(at, colon).trim(),
      value.replace("( ", "(").replace(" )", ")"),
    );
  }
  return declarations;
}

const ROOT = block(":root");
const DARK = block(".theme-dark");

function hex(value: string): Rgb {
  const digits = value.slice(1);
  return [0, 2, 4].map((at) =>
    Number.parseInt(digits.slice(at, at + 2), 16),
  ) as Rgb;
}

function mix(a: Rgb, b: Rgb, share: number): Rgb {
  return a.map(
    (channel, index) => channel * share + b[index]! * (1 - share),
  ) as Rgb;
}

function resolve(value: string, tokens: Declarations): Rgb {
  const text = value.trim();
  if (/^#[0-9a-f]{6}$/i.test(text)) return hex(text);
  const variable = /^var\((--[a-z0-9-]+)\)$/.exec(text);
  if (variable) {
    const declared = tokens.get(variable[1]!);
    if (declared === undefined) throw new Error(`unknown token ${variable[1]}`);
    return resolve(declared, tokens);
  }
  const prefix = "color-mix(in srgb,";
  if (text.startsWith(prefix) && text.endsWith(")")) {
    const args = text.slice(prefix.length, -1);
    const percent = args.indexOf("%,");
    const space = args.lastIndexOf(" ", percent);
    return mix(
      resolve(args.slice(0, space), tokens),
      resolve(args.slice(percent + 2), tokens),
      Number(args.slice(space + 1, percent)) / 100,
    );
  }
  throw new Error(`cannot resolve ${text}`);
}

function luminance(colour: Rgb): number {
  const [r, g, b] = colour.map((channel) => {
    const unit = channel / 255;
    return unit <= 0.03928 ? unit / 12.92 : ((unit + 0.055) / 1.055) ** 2.4;
  }) as Rgb;
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: Rgb, b: Rgb): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light! + 0.05) / (dark! + 0.05);
}

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
