import { describe, expect, it } from "vitest";

import { buildQuery } from "@/lib/query";

describe("buildQuery", () => {
  it("serialises present values and skips undefined / empty", () => {
    expect(
      buildQuery({ q: "guild", page: 0, kind: undefined, status: "" }),
    ).toBe("?q=guild&page=0");
  });

  it("returns an empty string when there is nothing to serialise", () => {
    expect(buildQuery({ a: undefined, b: "" })).toBe("");
  });
});
