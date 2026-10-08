import { describe, expect, it } from "vitest";

import { searchEmoji } from "@/features/recognition/domain/emoji-search";

const CATALOG = [
  {
    group: "Smileys",
    emoji: [
      { c: "😀", n: "grinning face" },
      { c: "😅", n: "grinning face with sweat" },
    ],
  },
  { group: "Objects", emoji: [{ c: "🚀", n: "rocket" }] },
];

describe("searchEmoji", () => {
  it("returns every group when the term is empty", () => {
    expect(searchEmoji(CATALOG, "")).toEqual(CATALOG);
  });

  it("matches on the name, case-insensitively", () => {
    expect(searchEmoji(CATALOG, "ROCK")).toEqual([
      { group: "Objects", emoji: [{ c: "🚀", n: "rocket" }] },
    ]);
  });

  it("drops a group with no match rather than returning it empty", () => {
    expect(searchEmoji(CATALOG, "sweat")).toHaveLength(1);
  });

  it("matches the character itself, so pasting an emoji finds it", () => {
    expect(searchEmoji(CATALOG, "🚀")[0]?.emoji[0]?.c).toBe("🚀");
  });
});
