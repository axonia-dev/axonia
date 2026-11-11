import { describe, expect, it } from "vitest";

import { contrast, DARK, resolve, ROOT } from "@/test/theme-tokens";

const THEMES = [
  { theme: "light", tokens: ROOT },
  { theme: "dark", tokens: new Map([...ROOT, ...DARK]) },
];

describe("Switch off track", () => {
  it.each(THEMES)(
    "holds 3:1 against the surface and the page in $theme",
    ({ tokens }) => {
      const edge = resolve("var(--control-edge)", tokens);
      for (const ground of ["--surface", "--background", "--surface-sunken"]) {
        expect(
          contrast(edge, resolve(`var(${ground})`, tokens)),
          ground,
        ).toBeGreaterThanOrEqual(3);
      }
    },
  );
});
