import { describe, expect, it } from "vitest";

import type { HealthSnapshot } from "@/features/health/domain/health";
import { rhythmWord } from "@/features/health/domain/rhythm";
import type { HealthStatus } from "@/features/health/domain/status";

function cadence(
  medianGapDays: number | null,
  status: HealthStatus = { code: "ON_RHYTHM", level: "GOOD" },
): HealthSnapshot["cadence"] {
  return {
    rhythm: "STEADY",
    sessionsHeld: 3,
    medianGapDays,
    nextSessionAt: null,
    lastHeldAt: null,
    trend: { previous: 3, direction: "FLAT" },
    status,
  };
}

describe("rhythmWord", () => {
  it.each([
    [0, "About weekly"],
    [10, "About weekly"],
    [11, "About every 2 weeks"],
    [21, "About every 2 weeks"],
    [22, "About monthly"],
    [45, "About monthly"],
    [46, "Every few months"],
    [120, "Every few months"],
  ])("reads a median gap of %i days as %s", (medianGapDays, word) => {
    // when
    const actual = rhythmWord(cadence(medianGapDays));

    // then
    expect(actual).toBe(word);
  });

  it("falls back to the status word when there is no median gap", () => {
    // when
    const actual = rhythmWord(
      cadence(null, { code: "SLOWING", level: "WATCH" }),
    );

    // then
    expect(actual).toBe("Slowing");
  });

  it("names a dormant community dormant rather than inventing a rhythm", () => {
    // when
    const actual = rhythmWord(
      cadence(null, { code: "DORMANT", level: "WORRY" }),
    );

    // then
    expect(actual).toBe("Dormant");
  });
});
