import { describe, expect, it } from "vitest";

import {
  classLists,
  shippedSourcesSaying,
  TREE_WALK_TIMEOUT_MS,
} from "@/test/source-files";

// A scroll container only clips the descendants whose containing block is inside it. An
// absolutely positioned element with no positioned ancestor - react-aria's visually-hidden
// input, one per Switch, Checkbox and Radio - resolves against the initial containing block
// instead, so it escapes the clip and stretches the *document's* scroll area down to wherever
// it sits. The symptom is a second scrollbar beside the container's own, scrolling nothing but
// empty space. The cure is one word, and a class list cannot show whether an ancestor already
// supplies the containing block, so every scroll container states it itself. No gate but this
// one can see it: jsdom has no layout.
const SCROLLS_VERTICALLY =
  /(?:^|\s)(?:(?:sm|md|lg|xl|2xl):)?overflow-(?:y-)?(?:auto|scroll)/;
const IS_CONTAINING_BLOCK =
  /(?:^|\s)(?:(?:sm|md|lg|xl|2xl):)?(?:relative|absolute|fixed|sticky)(?:\s|$)/;

describe("scroll containers", () => {
  it(
    "establish a containing block, so absolutely positioned content cannot escape the clip",
    async () => {
      const offenders: string[] = [];
      const sources = await shippedSourcesSaying("overflow-");
      for (const { name, source } of sources) {
        for (const classes of classLists(source)) {
          if (!SCROLLS_VERTICALLY.test(classes)) continue;
          if (IS_CONTAINING_BLOCK.test(classes)) continue;
          offenders.push(`${name}: ${classes}`);
        }
      }
      expect(sources.length).toBeGreaterThan(0);
      expect(offenders).toEqual([]);
    },
    TREE_WALK_TIMEOUT_MS,
  );
});
