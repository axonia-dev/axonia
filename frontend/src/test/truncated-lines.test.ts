import { describe, expect, it } from "vitest";

import {
  classLists,
  shippedSourcesSaying,
  TREE_WALK_TIMEOUT_MS,
} from "@/test/source-files";

// `truncate` is `white-space: nowrap` plus a clip, which makes the line one unbreakable run:
// its minimum width is whatever the cap allows, and a flex or grid parent cannot squeeze it
// below that. Cap it at an absolute length and the cap becomes a width the page has to find -
// a 22rem provenance line inside the ~200px an artifact card leaves on a phone widened the
// card, the list and the whole content area, which then scrolled sideways instead of the text
// ellipsing. A cap stated relative to the container (`max-w-full`, or a percentage) cannot do
// that, and is what truncation wants anyway: ellipse at the edge of whatever box I am in.
//
// This only forbids the combination. It does not prove a layout fits - a class list cannot see
// how narrow an ancestor is, so the truncating line that fits in one place and bursts in
// another looks identical here. e2e/mobile-layout.spec.ts is what measures a real box.
const TRUNCATES =
  /(?:^|\s)(?:(?:sm|md|lg|xl|2xl|max-sm|max-md|max-lg):)?truncate(?:\s|$)/;
// The cap's own value, read out and tested for a unit separately: one pattern spanning both
// (`max-w-\[[^\]]*(?:rem|px|em|ch)\]`) backtracks over the brackets and lints as unsafe.
const MAX_WIDTH = /(?:^|\s)(?:[a-z-]+:)?max-w-\[([^\]]*)\]/g;
const ABSOLUTE_UNIT = /rem|px|em|ch/;

function capsAtAnAbsoluteLength(classes: string): boolean {
  return [...classes.matchAll(MAX_WIDTH)].some((match) =>
    ABSOLUTE_UNIT.test(match[1]!),
  );
}

describe("truncated lines", () => {
  it(
    "cap their width against the container, not at an absolute length",
    async () => {
      const offenders: string[] = [];
      const sources = await shippedSourcesSaying("truncate");
      for (const { name, source } of sources) {
        for (const classes of classLists(source)) {
          if (!TRUNCATES.test(classes)) continue;
          if (!capsAtAnAbsoluteLength(classes)) continue;
          offenders.push(`${name}: ${classes}`);
        }
      }
      expect(sources.length).toBeGreaterThan(0);
      expect(offenders).toEqual([]);
    },
    TREE_WALK_TIMEOUT_MS,
  );
});
