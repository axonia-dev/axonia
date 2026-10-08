import { describe, expect, it } from "vitest";

import {
  detailFor,
  glyphFor,
  LOD_ICON_PX,
} from "@/features/expertise/ui/mark-lod";

describe("glyphFor", () => {
  it("gives every source type its own glyph", () => {
    expect(glyphFor("SLOT")).toBe("mic");
    expect(glyphFor("ARTIFACT")).toBe("doc");
    expect(glyphFor("TOPIC")).toBe("question");
    expect(glyphFor("ACTION_ITEM")).toBe("check");
  });

  it("never gives two source types the same glyph", () => {
    const all = (["SLOT", "ARTIFACT", "TOPIC", "ACTION_ITEM"] as const).map(
      glyphFor,
    );

    expect(new Set(all).size).toBe(all.length);
  });
});

describe("detailFor", () => {
  it("draws the real icon once it is legible", () => {
    expect(detailFor(LOD_ICON_PX)).toBe("icon");
    expect(detailFor(20)).toBe("icon");
  });

  it("falls back to a shape below that, where an icon is a blob", () => {
    expect(detailFor(LOD_ICON_PX - 0.01)).toBe("shape");
    expect(detailFor(4)).toBe("shape");
  });
});
