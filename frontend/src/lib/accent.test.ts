import { describe, expect, it } from "vitest";

import { accentForeground } from "@/lib/accent";

describe("accentForeground", () => {
  it("returns a dark foreground on a light accent", () => {
    // when
    const fg = accentForeground("#fbe54d");

    // then
    expect(fg).toBe("#1a1426");
  });

  it("returns white on a dark accent", () => {
    // when
    const fg = accentForeground("#1a1426");

    // then
    expect(fg).toBe("#ffffff");
  });
});
