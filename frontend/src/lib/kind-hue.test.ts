import { describe, expect, it } from "vitest";

import { kindHue } from "@/lib/kind-hue";

describe("kindHue", () => {
  it("returns one of the curated warm hues as a hex string", () => {
    // when
    const actual = kindHue("1");

    // then
    expect(actual).toMatch(/^#[0-9a-f]{6}$/i);
  });

  it("is deterministic for the same key", () => {
    // then
    expect(kindHue("42")).toBe(kindHue("42"));
  });

  it("spreads different keys across more than one hue", () => {
    // given
    const keys = ["1", "2", "3", "4", "5", "6", "7", "8"];

    // when
    const distinct = new Set(keys.map(kindHue));

    // then
    expect(distinct.size).toBeGreaterThan(1);
  });
});
