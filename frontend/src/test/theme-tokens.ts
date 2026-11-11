/// <reference types="node" />
import { readFileSync } from "node:fs";
import { join } from "node:path";

import { SRC } from "@/test/source-files";

// jsdom resolves no custom property and computes no colour, so this reads the theme tokens out of
// globals.css and resolves them the way the cascade does on the root element: the theme block over
// :root, then whatever a caller layers on top (the community accent the shell writes inline). Only
// the forms the theme tokens use are understood (a hex, a var() and an srgb color-mix), and anything else fails loudly rather
// than measuring a guess.

export type Rgb = [number, number, number];
export type Declarations = Map<string, string>;

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

export const ROOT = block(":root");
export const DARK = block(".theme-dark");

function hex(value: string): Rgb {
  const digits = value.slice(1);
  return [0, 2, 4].map((at) =>
    Number.parseInt(digits.slice(at, at + 2), 16),
  ) as Rgb;
}

export function mix(a: Rgb, b: Rgb, share: number): Rgb {
  return a.map(
    (channel, index) => channel * share + b[index]! * (1 - share),
  ) as Rgb;
}

export function resolve(value: string, tokens: Declarations): Rgb {
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

export function contrast(a: Rgb, b: Rgb): number {
  const [light, dark] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (light! + 0.05) / (dark! + 0.05);
}
