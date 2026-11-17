import { describe, expect, it } from "vitest";

import { AREA_HUES, areaHue } from "@/lib/area-hue";
import { contrast, DARK, resolve, ROOT } from "@/test/theme-tokens";

const COMMON_AREAS = [
  "kafka",
  "testing",
  "code-review",
  "architecture",
  "refactoring",
  "kubernetes",
  "react",
  "java",
  "security",
  "observability",
];

const THEMES = [
  { theme: "light" as const, tokens: ROOT },
  { theme: "dark" as const, tokens: new Map([...ROOT, ...DARK]) },
];

describe("areaHue", () => {
  it("gives one area the same tint every time", () => {
    // given
    const first = areaHue("code-review");

    // when
    const actual = areaHue("Code Review");

    // then
    expect(actual).toBe(first);
  });

  it("spreads ten common areas over at least six tints", () => {
    // when
    const actual = new Set(COMMON_AREAS.map((area) => areaHue(area)));

    // then
    expect(actual.size).toBeGreaterThanOrEqual(6);
  });

  it.each(THEMES)(
    "every tint meets 3:1 against the $theme surface",
    ({ theme, tokens }) => {
      for (const hue of AREA_HUES) {
        // given
        const tint = hue[theme];

        // when
        const actual = contrast(
          resolve(tint.foreground, tokens),
          resolve(tint.background, tokens),
        );

        // then
        expect(actual, tint.foreground).toBeGreaterThanOrEqual(4.5);
        for (const ground of [
          "--surface",
          "--background",
          "--surface-sunken",
        ]) {
          expect(
            contrast(
              resolve(tint.border, tokens),
              resolve(`var(${ground})`, tokens),
            ),
            `${tint.border} on ${ground}`,
          ).toBeGreaterThanOrEqual(3);
        }
      }
    },
  );
});
