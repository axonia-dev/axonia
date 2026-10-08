import { describe, expect, it } from "vitest";

import {
  shippedSourcesSaying,
  TREE_WALK_TIMEOUT_MS,
} from "@/test/source-files";

// `@theme inline` declares every `--color-*` alias on `:root`, and a custom property's value is
// substituted where it is declared - so `--color-primary` resolves to the root's `--primary`
// once and inherits that one colour everywhere. A component reading the alias therefore ignores
// every override below the root: a community's accent, and anything else re-assigning a token.
// Tailwind's own utilities are safe, because `inline` makes them emit `var(--primary)` directly;
// only hand-written CSS in a class list or a style value can reach for the alias. Read the token
// instead. No other gate sees this: the alias resolves to a real colour, so nothing errors and
// jsdom reports no computed style at all.
const ALIAS = /var\(--color-[a-z-]+/g;

describe("theme variables", () => {
  it(
    "are read as tokens, never through the root-resolved --color-* aliases",
    async () => {
      const offenders: string[] = [];
      // Every module reading a variable, not just an alias: with no offender left, an alias-only
      // corpus would be empty, and an empty corpus cannot tell a clean tree from an unread one.
      const sources = await shippedSourcesSaying("var(--");
      for (const { name, source } of sources) {
        for (const match of source.matchAll(ALIAS)) {
          offenders.push(`${name}: ${match[0]})`);
        }
      }
      expect(sources.length).toBeGreaterThan(0);
      expect(offenders).toEqual([]);
    },
    TREE_WALK_TIMEOUT_MS,
  );
});
