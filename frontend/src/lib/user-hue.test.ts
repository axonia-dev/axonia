import { describe, expect, it } from "vitest";

import { userHue } from "@/lib/user-hue";

describe("userHue", () => {
  it("is deterministic for a given key", () => {
    expect(userHue("sub-123")).toBe(userHue("sub-123"));
  });

  it("returns a hex colour from the palette", () => {
    expect(userHue("sub-123")).toMatch(/^#[0-9a-f]{6}$/i);
  });

  it("returns a stable colour for the empty string (no crash)", () => {
    expect(userHue("")).toMatch(/^#[0-9a-f]{6}$/i);
  });
});
