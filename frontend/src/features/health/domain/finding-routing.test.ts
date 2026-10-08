import { describe, expect, it } from "vitest";

import type { Chapter } from "@/features/health/domain/finding-routing";
import {
  CHAPTER_OF,
  findingsFor,
} from "@/features/health/domain/finding-routing";
import type { Finding, FindingCode } from "@/features/health/domain/health";

// Derived, never hand-copied: a list typed out here would silently stay ten codes long while
// FindingCode grew to eleven, and the guard below would then pass while missing the new one.
const ALL_CODES = Object.keys(CHAPTER_OF) as FindingCode[];
const CHAPTERS: Chapter[] = ["carried", "alive", "payoff"];

describe("finding routing", () => {
  it("files every finding code under exactly one of the three chapters", () => {
    // Totality is `tsc -b`'s guarantee and not this loop's: CHAPTER_OF is a
    // Record<FindingCode, Chapter>, so a code added later fails the build until it is routed. What
    // this adds is that reading the three chapters back yields every code, once each - a code
    // routed to a chapter the page does not render would otherwise vanish silently.
    const findings: Finding[] = ALL_CODES.map((code) => ({
      kind: "RISK",
      code,
    }));

    const routed = CHAPTERS.flatMap((chapter) =>
      findingsFor(chapter, findings).map((finding) => finding.code),
    );

    expect(routed).toHaveLength(ALL_CODES.length);
    expect(new Set(routed)).toEqual(new Set(ALL_CODES));
  });

  it("puts the fragility findings in the chapter that shows the evidence for them", () => {
    expect(CHAPTER_OF.SINGLE_POINT).toBe("carried");
    expect(CHAPTER_OF.NARROW_REACH).toBe("carried");
    expect(CHAPTER_OF.DORMANT).toBe("alive");
    expect(CHAPTER_OF.KNOWLEDGE_REUSED).toBe("payoff");
  });

  it("keeps the diagnosis order within a chapter", () => {
    const findings: Finding[] = [
      { kind: "RISK", code: "SINGLE_POINT" },
      { kind: "STRENGTH", code: "ON_RHYTHM" },
      { kind: "GAP", code: "NARROW_REACH" },
    ];

    expect(findingsFor("carried", findings)).toEqual([
      { kind: "RISK", code: "SINGLE_POINT" },
      { kind: "GAP", code: "NARROW_REACH" },
    ]);
  });
});
