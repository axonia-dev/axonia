import { describe, expect, it } from "vitest";

import {
  classLists,
  shippedSourcesSaying,
  TREE_WALK_TIMEOUT_MS,
} from "@/test/source-files";

// A grid track declared only at a breakpoint leaves the phone layout on the implicit
// track, which is minmax(auto, …). An `auto` floor is the content's min-content width,
// and `truncate` makes a line one unbreakable run, so a long word grows the column past
// the viewport and every card in it overflows. Tailwind's grid-cols-N expands to
// repeat(N, minmax(0, 1fr)), whose floor is 0, so declaring the base column count is what
// keeps the layout inside the screen. No gate but this one can see it: jsdom has no layout.
const RESPONSIVE_COLUMNS =
  /(?:^|\s)(?:sm|md|lg|xl|2xl|max-sm|max-md|max-lg):grid-cols-/;
const BASE_COLUMNS = /(?:^|\s)grid-cols-/;

// An arbitrary template has the same trap inside it: a bare `1fr` track is minmax(auto, 1fr), so
// one long title widens it past its container. `minmax(0,1fr)` floors it at zero, a
// `minmax(auto,1fr)` spells the trap out, and a `repeat(3,1fr)` repeats the bare track.
const ARBITRARY_TEMPLATE = /grid-(?:cols|rows)-\[([^\]\s]*)\]/g;
const CONTENT_SIZED_FLOORS = new Set(["auto", "min-content", "max-content"]);

function isBareFraction(track: string): boolean {
  const amount = track.slice(0, -2);
  return track.endsWith("fr") && amount !== "" && !Number.isNaN(Number(amount));
}

function closingParen(text: string, open: number): number {
  let depth = 0;
  for (let at = open; at < text.length; at++) {
    if (text.charAt(at) === "(") depth++;
    if (text.charAt(at) === ")" && --depth === 0) return at;
  }
  return text.length;
}

function floorAndMax(args: string): [string, string] {
  let depth = 0;
  for (let at = 0; at < args.length; at++) {
    if (args.charAt(at) === "(") depth++;
    if (args.charAt(at) === ")") depth--;
    if (args.charAt(at) === "," && depth === 0) {
      return [args.slice(0, at), args.slice(at + 1)];
    }
  }
  return [args, ""];
}

/** Each minmax() replaced by the track its floor leaves exposed: none behind a fixed floor, the
 *  max behind a content-sized one. Parentheses are matched, so a nested min() stays inside. */
function withoutFlooredTracks(template: string): string {
  const start = template.indexOf("minmax(");
  if (start === -1) return template;
  const open = start + "minmax".length;
  const close = closingParen(template, open);
  const [floor, max] = floorAndMax(template.slice(open + 1, close));
  const exposed = CONTENT_SIZED_FLOORS.has(floor.replaceAll("_", ""))
    ? max
    : "";
  return (
    template.slice(0, start) +
    exposed +
    withoutFlooredTracks(template.slice(close + 1))
  );
}

function hasBareFraction(template: string): boolean {
  return withoutFlooredTracks(template)
    .split(/[_,()]/)
    .some((track) => isBareFraction(track));
}

describe("bare fraction detection", () => {
  it("sees a bare or repeated fraction track", () => {
    expect(hasBareFraction("1fr_auto")).toBe(true);
    expect(hasBareFraction("repeat(3,1fr)")).toBe(true);
  });

  it("accepts a fraction floored at zero or at a fixed size", () => {
    expect(hasBareFraction("auto_minmax(0,1fr)_84px")).toBe(false);
    expect(hasBareFraction("repeat(auto-fill,minmax(12rem,1fr))")).toBe(false);
  });

  it("accepts a floor computed by a nested function", () => {
    expect(hasBareFraction("minmax(min(12rem,100%),1fr)")).toBe(false);
    expect(hasBareFraction("minmax(max(0px,calc(50%-1rem)),1fr)_2rem")).toBe(
      false,
    );
  });

  it("sees a bare fraction beside a nested floor", () => {
    expect(hasBareFraction("minmax(min(12rem,100%),1fr)_1fr")).toBe(true);
  });

  it("sees a fraction behind a content-sized floor", () => {
    expect(hasBareFraction("minmax(auto,1fr)")).toBe(true);
    expect(hasBareFraction("auto_minmax(min-content,_2fr)")).toBe(true);
  });
});

describe("grid tracks", () => {
  it(
    "declares a base column count wherever it declares one at a breakpoint",
    async () => {
      const offenders: string[] = [];
      const sources = await shippedSourcesSaying("grid-");
      for (const { name, source } of sources) {
        for (const classes of classLists(source)) {
          if (!RESPONSIVE_COLUMNS.test(classes)) continue;
          if (BASE_COLUMNS.test(classes)) continue;
          offenders.push(`${name}: ${classes}`);
        }
      }
      expect(sources.length).toBeGreaterThan(0);
      expect(offenders).toEqual([]);
    },
    TREE_WALK_TIMEOUT_MS,
  );

  it(
    "floors every fractional track of an arbitrary template at zero",
    async () => {
      const offenders: string[] = [];
      const sources = await shippedSourcesSaying("grid-");
      for (const { name, source } of sources) {
        for (const [template, tracks] of source.matchAll(ARBITRARY_TEMPLATE)) {
          if (hasBareFraction(tracks!)) offenders.push(`${name}: ${template}`);
        }
      }
      expect(sources.length).toBeGreaterThan(0);
      expect(offenders).toEqual([]);
    },
    TREE_WALK_TIMEOUT_MS,
  );
});
