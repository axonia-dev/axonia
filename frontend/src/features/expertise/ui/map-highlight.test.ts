import { describe, expect, it } from "vitest";

import type { Reading } from "@/features/expertise/ui/expertise-readings";
import {
  highlightFor,
  isLit,
  markLit,
  withSearch,
} from "@/features/expertise/ui/map-highlight";

function reading(...tags: string[]): Reading {
  return {
    id: "recent",
    kind: "rank",
    statement: "",
    rows: tags.map((tag) => ({ tag, value: "" })),
    total: tags.length,
    badgeOf: () => null,
  };
}

describe("highlightFor", () => {
  it("lights the areas a reading listed", () => {
    expect([...highlightFor(reading("kafka", "flink"), null)!]).toStrictEqual([
      "kafka",
      "flink",
    ]);
  });

  it("lights the one area chosen under the neighbourhoods rendering", () => {
    expect([...highlightFor(null, "kafka")!]).toStrictEqual(["kafka"]);
  });

  it("lights the whole map when the panel is pointing at nothing in particular", () => {
    expect(highlightFor(null, null)).toBeNull();
  });

  /** Dimming every area to say "no area matched" leaves a reader looking at a drawing that seems broken, and the
   *  panel has said it in words already. */
  it("lights the whole map for a reading that answered with nothing", () => {
    expect(highlightFor(reading(), null)).toBeNull();
  });

  it("prefers the reading to a selection left over from before it", () => {
    expect([...highlightFor(reading("flink"), "kafka")!]).toStrictEqual([
      "flink",
    ]);
  });
});

describe("isLit", () => {
  it("lights everything when nothing in particular is lit", () => {
    expect(isLit(null, ["kafka"])).toBe(true);
    expect(isLit(null, [])).toBe(true);
  });

  it("lights a name covering any area the panel is about", () => {
    const highlight = new Set(["kafka"]);

    expect(isLit(highlight, ["flink", "kafka"])).toBe(true);
    expect(isLit(highlight, ["flink"])).toBe(false);
    expect(isLit(highlight, [])).toBe(false);
  });
});

describe("withSearch", () => {
  const found = (
    tags: string[],
    lit: number[],
  ): { tags: Set<string>; hits: Set<number>; lit: Set<number> } => ({
    tags: new Set(tags),
    hits: new Set(lit),
    lit: new Set(lit),
  });

  it("leaves the panel's answer alone, and every mark to its own area, with no term typed", () => {
    expect(withSearch(new Set(["kafka"]), null)).toStrictEqual({
      tags: new Set(["kafka"]),
      marks: null,
    });
  });

  /** Said in words beside the box instead. A map dimmed end to end reads as broken rather than as "no matches". */
  it("leaves the map as it was for a term that matched nothing", () => {
    expect(withSearch(new Set(["kafka"]), found([], []))).toStrictEqual({
      tags: new Set(["kafka"]),
      marks: null,
    });
  });

  it("asks the map about what a term found rather than about what the panel is on", () => {
    expect(withSearch(new Set(["kafka"]), found(["flink"], [7]))).toStrictEqual(
      {
        tags: new Set(["flink"]),
        marks: new Set([7]),
      },
    );
  });

  /** A talk found by its title stands on ground nothing matched, and that ground is exactly what should go quiet. */
  it("sets every area back for a term only a contribution answered", () => {
    expect(withSearch(null, found([], [7]))).toStrictEqual({
      tags: new Set([]),
      marks: new Set([7]),
    });
  });
});

describe("markLit", () => {
  const mark = { signalId: 7, tag: "kafka" };

  it("leaves a mark to its own area when nothing has been searched for", () => {
    expect(markLit({ tags: new Set(["kafka"]), marks: null }, mark)).toBe(true);
    expect(markLit({ tags: new Set(["flink"]), marks: null }, mark)).toBe(
      false,
    );
  });

  it("lights the marks a term picked out, wherever they stand", () => {
    const highlight = { tags: new Set(["flink"]), marks: new Set([7]) };

    expect(markLit(highlight, mark)).toBe(true);
    expect(markLit(highlight, { signalId: 8, tag: "flink" })).toBe(false);
  });
});
