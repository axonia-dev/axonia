import { describe, expect, it } from "vitest";

import type { MapContribution } from "@/features/expertise/domain/expertise";
import {
  facedSignalIds,
  reachThreshold,
} from "@/features/expertise/ui/face-gate";

function slot(signalId: number, reach: number | null): MapContribution {
  return {
    signalId,
    canonicalTag: "kafka",
    userId: `u${signalId}`,
    reach,
    evidence: {
      signalType: "PRESENTED",
      sourceType: "SLOT",
      sourceId: signalId,
      label: null,
      communitySlug: "backend-guild",
      occurredAt: "2026-09-01T10:00:00Z",
      sessionId: null,
      typeId: null,
      typeLabel: null,
      typeIcon: null,
      typeAccentColor: null,
    },
  };
}

function artifact(signalId: number): MapContribution {
  return {
    ...slot(signalId, null),
    evidence: {
      ...slot(signalId, null).evidence,
      signalType: "CONTRIBUTED",
      sourceType: "ARTIFACT",
    },
  };
}

describe("reachThreshold", () => {
  it("is the 75th percentile of the reaches it knows", () => {
    const marks = [4, 8, 12, 16, 20].map((r, i) => slot(i + 1, r));

    expect(reachThreshold(marks)).toBe(16);
  });

  it("ignores a contribution whose reach is unknown", () => {
    const marks = [slot(1, 10), slot(2, 20), slot(3, null)];

    expect(reachThreshold(marks)).toBe(20);
  });

  it("is null when nothing has a known reach", () => {
    expect(reachThreshold([slot(1, null), artifact(2)])).toBeNull();
  });

  it("ignores a talk nobody attended", () => {
    const marks = [slot(1, 0), slot(2, 10), slot(3, 20)];

    expect(reachThreshold(marks)).toBe(20);
  });

  it("is null when every measured talk had an empty room", () => {
    expect(reachThreshold([slot(1, 0), slot(2, 0), slot(3, 0)])).toBeNull();
  });

  it("is null when there are no contributions at all", () => {
    expect(reachThreshold([])).toBeNull();
  });

  it("works for a community with a single measured talk", () => {
    expect(reachThreshold([slot(1, 6)])).toBe(6);
  });
});

describe("facedSignalIds", () => {
  it("faces only the contributions at or above the threshold", () => {
    const marks = [4, 8, 12, 16, 20].map((r, i) => slot(i + 1, r));

    expect([...facedSignalIds(marks)].sort((a, b) => a - b)).toEqual([4, 5]);
  });

  it("never faces an artifact, however many people saw it", () => {
    const marks = [slot(1, 100), { ...artifact(2), reach: 500 }];

    expect(facedSignalIds(marks)).toEqual(new Set([1]));
  });

  it("faces nobody when the reach of every talk is unknown", () => {
    expect(facedSignalIds([slot(1, null), slot(2, null)])).toEqual(new Set());
  });

  // What a community that never used attendance tracking actually sends: the reader measures
  // every resolvable slot and reports zero, so a reach of 0 arrives where null never does.
  it("faces nobody when no attendance was ever recorded", () => {
    const marks = Array.from({ length: 8 }, (_, i) => slot(i + 1, 0));

    expect(facedSignalIds(marks)).toEqual(new Set());
  });

  it("faces only the well attended talks when most rooms were empty", () => {
    const empty = Array.from({ length: 12 }, (_, i) => slot(i + 1, 0));
    const attended = [10, 20, 30, 40, 50].map((r, i) => slot(13 + i, r));

    expect(
      [...facedSignalIds([...empty, ...attended])].sort((a, b) => a - b),
    ).toEqual([16, 17]);
  });

  it("keeps faces a minority of a realistic picture", () => {
    const unrecorded = Array.from({ length: 200 }, (_, i) => slot(i + 1, 0));
    const attended = Array.from({ length: 40 }, (_, i) => slot(300 + i, i + 1));
    const artifacts = Array.from({ length: 760 }, (_, i) => artifact(1000 + i));
    const all = [...unrecorded, ...attended, ...artifacts];

    const share = facedSignalIds(all).size / all.length;

    expect(share).toBeLessThan(0.15);
  });
});
