import { describe, expect, it } from "vitest";

import { accentVariables } from "@/features/community/ui/community-accent";
import { COMMUNITY_PALETTE } from "@/lib/kind-hue";
import {
  contrast,
  DARK,
  type Declarations,
  resolve,
  ROOT,
} from "@/test/theme-tokens";

const THEMES = ["light", "dark"] as const;
const GROUNDS = ["--surface", "--background", "--surface-sunken"];
const INKS = ["--primary-ink", "--primary-ink-strong"];

function themed(theme: (typeof THEMES)[number]): Declarations {
  return new Map([...ROOT, ...(theme === "dark" ? DARK : [])]);
}

function inCommunity(
  theme: (typeof THEMES)[number],
  hue: string,
): Declarations {
  const accent = Object.entries(
    accentVariables({ accentColor: hue, kindId: 1 }),
  ) as [string, string][];
  return new Map([...themed(theme), ...accent]);
}

const CASES = [
  ...THEMES.map((theme) => ({
    theme,
    hue: "the product's own",
    tokens: themed(theme),
  })),
  ...THEMES.flatMap((theme) =>
    COMMUNITY_PALETTE.map((hue) => ({
      theme,
      hue,
      tokens: inCommunity(theme, hue),
    })),
  ),
];

describe("accent inks", () => {
  it.each(CASES)(
    "hold 4.5:1 on every ground in $theme for $hue accent",
    ({ tokens }) => {
      for (const ink of INKS) {
        for (const ground of GROUNDS) {
          expect(
            contrast(
              resolve(`var(${ink})`, tokens),
              resolve(`var(${ground})`, tokens),
            ),
            `${ink} on ${ground}`,
          ).toBeGreaterThanOrEqual(4.5);
        }
      }
    },
  );

  it("leave the product's dark theme in its own primary, which already reads", () => {
    const tokens = themed("dark");

    expect(resolve("var(--primary-ink)", tokens)).toEqual(
      resolve("var(--primary)", tokens),
    );
    expect(resolve("var(--primary-ink-strong)", tokens)).toEqual(
      resolve("var(--primary-strong)", tokens),
    );
  });
});
