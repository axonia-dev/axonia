import { describe, expect, it } from "vitest";

import {
  classLists,
  shippedSourcesSaying,
  TREE_WALK_TIMEOUT_MS,
} from "@/test/source-files";

// Tailwind v4 draws a divider in currentColor unless a divide colour is named, so a bare
// divide-y paints a full-contrast rule between rows where every other list uses the border
// token. jsdom has no paint, so nothing but this guard sees it.
const DIVIDER_WIDTH = /^divide-[xy](?!-reverse)/;
const NOT_A_COLOUR =
  /^divide-(?:[xy](?:-|$)|solid$|dashed$|dotted$|double$|none$)/;

/** Cuts at the last `:` outside brackets, so a bracketed variant (`data-[state=open]:`) and a
 *  bracketed value (`divide-[color:var(--x)]`) each stay on their own side. */
function split(token: string): { variant: string; utility: string } {
  let depth = 0;
  let cut = -1;
  [...token].forEach((character, index) => {
    if (character === "[" || character === "(") depth += 1;
    if (character === "]" || character === ")") depth -= 1;
    if (character === ":" && depth === 0) cut = index;
  });
  return { variant: token.slice(0, cut + 1), utility: token.slice(cut + 1) };
}

/** The dividers in `classes` that no colour reaches: an unprefixed colour covers every divider,
 *  a prefixed one only the dividers under the same prefix. */
function uncolouredDividers(classes: string): string[] {
  const tokens = classes.split(/\s+/).filter(Boolean).map(split);
  const colouredVariants = new Set(
    tokens
      .filter(({ utility }) => utility.startsWith("divide-"))
      .filter(({ utility }) => !NOT_A_COLOUR.test(utility))
      .map(({ variant }) => variant),
  );
  return tokens
    .filter(({ utility }) => DIVIDER_WIDTH.test(utility))
    .filter(
      ({ variant }) =>
        !colouredVariants.has("") && !colouredVariants.has(variant),
    )
    .map(({ variant, utility }) => `${variant}${utility}`);
}

describe("divider colour detection", () => {
  it("sees a divider with no colour", () => {
    expect(uncolouredDividers("flex flex-col divide-y rounded-md")).toEqual([
      "divide-y",
    ]);
    expect(uncolouredDividers("divide-x-2 divide-dashed")).toEqual([
      "divide-x-2",
    ]);
  });

  it("sees a divider under a bracketed variant", () => {
    expect(uncolouredDividers("data-[state=open]:divide-y")).toEqual([
      "data-[state=open]:divide-y",
    ]);
    expect(uncolouredDividers("[&>li]:divide-y")).toEqual(["[&>li]:divide-y"]);
    expect(uncolouredDividers("max-[640px]:divide-y")).toEqual([
      "max-[640px]:divide-y",
    ]);
  });

  it("accepts a bracketed variant divider coloured under the same variant", () => {
    expect(
      uncolouredDividers("max-[640px]:divide-y max-[640px]:divide-border"),
    ).toEqual([]);
  });

  it("accepts a divider carrying the border token", () => {
    expect(uncolouredDividers("divide-border flex divide-x")).toEqual([]);
  });

  it("ignores a reversal, which draws nothing", () => {
    expect(uncolouredDividers("flex-col-reverse divide-y-reverse")).toEqual([]);
  });

  it("sees a divider whose colour is named only under a variant", () => {
    expect(uncolouredDividers("dark:divide-border divide-y")).toEqual([
      "divide-y",
    ]);
  });

  it("accepts a variant divider coloured by an unprefixed or matching colour", () => {
    expect(uncolouredDividers("md:divide-x divide-border")).toEqual([]);
    expect(uncolouredDividers("md:divide-x md:divide-border")).toEqual([]);
  });

  it("accepts an arbitrary colour", () => {
    expect(uncolouredDividers("divide-[var(--border)] divide-y")).toEqual([]);
    expect(uncolouredDividers("divide-(--border) divide-y")).toEqual([]);
    expect(
      uncolouredDividers("dark:divide-[color:var(--x)] dark:divide-y"),
    ).toEqual([]);
  });
});

describe("dividers", () => {
  it(
    "name a colour wherever they draw one",
    async () => {
      const offenders: string[] = [];
      const sources = await shippedSourcesSaying("divide-");
      for (const { name, source } of sources) {
        for (const classes of classLists(source)) {
          if (uncolouredDividers(classes).length === 0) continue;
          offenders.push(`${name}: ${classes}`);
        }
      }
      expect(sources.length).toBeGreaterThan(0);
      expect(offenders).toEqual([]);
    },
    TREE_WALK_TIMEOUT_MS,
  );
});
