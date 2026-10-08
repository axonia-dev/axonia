import { describe, expect, it } from "vitest";

import {
  accentVariables,
  communityHue,
  focalPosition,
} from "@/features/community/ui/community-accent";
import { COMMUNITY_PALETTE, kindHue } from "@/lib/kind-hue";

describe("communityHue", () => {
  it("is the accent the Organizer chose", () => {
    expect(communityHue({ accentColor: "#f2603c", kindId: 3 })).toBe("#f2603c");
  });

  it("falls back to the kind's hue, which is drawn from the same palette", () => {
    expect(communityHue({ accentColor: null, kindId: 3 })).toBe(kindHue("3"));
  });

  it("never falls back to a colour the picker does not offer", () => {
    for (let kindId = 1; kindId <= 50; kindId += 1) {
      expect(COMMUNITY_PALETTE).toContain(
        communityHue({ accentColor: null, kindId }),
      );
    }
  });
});

describe("accentVariables", () => {
  it("carries the hue and a readable foreground for solid fills", () => {
    const style = accentVariables({
      accentColor: "#f2603c",
      kindId: 3,
    }) as Record<string, string>;

    expect(style["--community-accent"]).toBe("#f2603c");
    expect(style["--primary"]).toBe("#f2603c");
    expect(style["--primary-foreground"]).toBe("#ffffff");
  });

  it("rings focus in the accent, mixed toward the foreground so it holds 3:1 on the surface", () => {
    const style = accentVariables({
      accentColor: "#d9871f",
      kindId: 3,
    }) as Record<string, string>;

    expect(style["--focus-ring"]).toBe(
      "color-mix(in srgb, #d9871f 85%, var(--foreground))",
    );
  });

  it("still carries a hue when no accent is set, so the shell is never uncoloured", () => {
    const style = accentVariables({ accentColor: null, kindId: 3 }) as Record<
      string,
      string
    >;

    expect(style["--community-accent"]).toBe(kindHue("3"));
  });
});

describe("focalPosition", () => {
  it("renders a stored point as an object position", () => {
    expect(focalPosition({ x: 50, y: 78 })).toBe("50% 78%");
  });

  it("falls back to the centre the product used to hardcode", () => {
    expect(focalPosition(null)).toBe("50% 50%");
    expect(focalPosition(undefined)).toBe("50% 50%");
  });
});
