import { describe, expect, it } from "vitest";

import { CHAPTER_OF } from "@/features/health/domain/finding-routing";
import type {
  Finding,
  FindingCode,
  FindingKind,
} from "@/features/health/domain/health";
import {
  busFactorRead,
  CADENCE_WORD,
  cadenceEvidence,
  findingText,
  formatShare,
  healthSnapshotSchema,
} from "@/features/health/domain/health";

const raw = {
  window: "90d",
  generatedAt: "2026-06-11T12:00:00Z",
  busFactor: 2,
  concentration: { top3Share: 0.8, distribution: [0.4, 0.28, 0.12, 0.1, 0.1] },
  cadence: {
    status: "STEADY",
    sessionsHeld: 6,
    medianGapDays: 14,
    trend: { previous: 5, direction: "UP" },
  },
  participation: {
    activeMembers: 32,
    totalMembers: 80,
    share: 0.4,
    trend: { previous: 0.28, direction: "UP" },
  },
  engagement: { loves: 41, trend: { previous: 30, direction: "UP" } },
  membershipGrowth: {
    joins: 5,
    memberCount: 80,
    trend: { previous: 2, direction: "UP" },
  },
  reach: {
    available: true,
    teams: 6,
    departments: 3,
    businessUnits: 2,
    contributorsWithUnit: 12,
    totalContributors: 12,
    trend: { previous: 4, direction: "UP" },
  },
  reuse: {
    opened: 40,
    artifactsOpened: 12,
    openingsTrend: { previous: 31, direction: "UP" },
    saves: 18,
    artifactsSaved: 7,
    helped: 2,
    notHelped: 0,
    savesTrend: { previous: 12, direction: "UP" },
  },
  diagnosis: {
    findings: [{ kind: "GAP", code: "INTEREST_WITHOUT_PAYOFF" }],
  },
  series: {
    contributors: [1, 0, 2, 0, 0, 1, 0, 0, 3, 0, 0, 1],
    participation: [4, 4, 5, 5, 5, 6, 6, 6, 7, 7, 7, 8],
    engagement: [0, 1, 1, 2, 3, 5, 8, 8, 9, 9, 10, 12],
    joins: [1, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0, 1],
    openings: [2, 3, 1, 0, 4, 6, 5, 7, 8, 2, 1, 9],
    saves: [0, 1, 0, 0, 1, 2, 1, 0, 3, 0, 0, 2],
  },
  heldSessionDates: ["2026-05-02T17:00:00Z"],
};

const reuse = {
  opened: 40,
  artifactsOpened: 12,
  openingsTrend: { previous: 31, direction: "UP" as const },
  saves: 18,
  artifactsSaved: 7,
  helped: 2,
  notHelped: 0,
  savesTrend: { previous: 12, direction: "UP" as const },
};

describe("healthSnapshotSchema", () => {
  it("parses a full snapshot", () => {
    // when
    const actual = healthSnapshotSchema.parse(raw);

    // then
    expect(actual.busFactor).toBe(2);
    expect(actual.concentration.distribution).toHaveLength(5);
  });

  it("accepts a null median gap (fewer than two held sessions)", () => {
    // when
    const actual = healthSnapshotSchema.parse({
      ...raw,
      cadence: { ...raw.cadence, medianGapDays: null },
    });

    // then
    expect(actual.cadence.medianGapDays).toBeNull();
  });
});

describe("busFactorRead", () => {
  it.each([
    [0, "No activity"],
    [1, "Fragile"],
    [2, "Concentrated"],
    [3, "Shared"],
    [4, "Shared"],
    [5, "Well-distributed"],
  ])("reads bus factor %i as %s", (busFactor, read) => {
    expect(busFactorRead(busFactor)).toBe(read);
  });
});

it("no longer exports a spread nudge, because the SINGLE_POINT finding says it", async () => {
  const domain = await import("@/features/health/domain/health");

  expect("spreadNudge" in domain).toBe(false);
});

it("no longer exports a cadence sentence, because the findings own the sentences", async () => {
  const domain = await import("@/features/health/domain/health");

  expect("CADENCE_READ" in domain).toBe(false);
});

describe("CADENCE_WORD", () => {
  it("names each status in a word or two, never a sentence", () => {
    expect(CADENCE_WORD).toEqual({
      STEADY: "On rhythm",
      SLOWING: "Slowing",
      STALLED: "Stalled",
      DORMANT: "Dormant",
    });
  });

  it("can never collide with a finding, because no finding is that short", () => {
    const words = Object.values(CADENCE_WORD);
    const snapshot = healthSnapshotSchema.parse({ ...raw, reuse });

    for (const code of Object.keys(CHAPTER_OF) as FindingCode[]) {
      expect(words).not.toContain(findingText(code, snapshot));
    }
  });
});

describe("cadenceEvidence", () => {
  const cadence = (medianGapDays: number | null) =>
    ({
      status: "STEADY",
      sessionsHeld: 4,
      medianGapDays,
      trend: { previous: 3, direction: "UP" },
    }) as const;

  it("states the median gap as a fact about the window, not as a live rhythm", () => {
    expect(cadenceEvidence(cadence(14))).toBe(
      "Median gap of 14 days between sessions.",
    );
  });

  it("singularises a one-day gap", () => {
    expect(cadenceEvidence(cadence(1))).toBe(
      "Median gap of 1 day between sessions.",
    );
  });

  it("keeps a floored zero-day gap a claim about the median, not about every session", () => {
    // Gaps of 0.5, 0.5, 30 and 40 days have a median of 0 and two long silences, so
    // anything phrased about "sessions" rather than about the median is false for it.
    expect(cadenceEvidence(cadence(0))).toBe(
      "Median gap under a day between sessions.",
    );
  });

  it("states nothing at all when there is no gap to state", () => {
    expect(cadenceEvidence(cadence(null))).toBeNull();
  });
});

describe("formatShare", () => {
  it("renders a 0..1 share as a percentage", () => {
    expect(formatShare(0.4)).toBe("40%");
  });
});

describe("reuse + diagnosis schema", () => {
  it("parses the reuse dial and diagnosis findings", () => {
    // when
    const parsed = healthSnapshotSchema.parse({
      ...raw,
      reuse,
      diagnosis: {
        findings: [{ kind: "GAP", code: "INTEREST_WITHOUT_PAYOFF" }],
      },
    });

    // then
    expect(parsed.reuse.saves).toBe(18);
    expect(parsed.diagnosis.findings[0]?.code).toBe("INTEREST_WITHOUT_PAYOFF");
  });

  it("shapes a single finding as a kind + code pair", () => {
    // given
    const finding: Finding = { kind: "GAP", code: "INTEREST_WITHOUT_PAYOFF" };

    // when
    const kind: FindingKind = finding.kind;

    // then
    expect(kind).toBe("GAP");
  });
});

describe("findingText", () => {
  it("phrases interest-without-payoff from the reuse numbers", () => {
    // when
    const snapshot = healthSnapshotSchema.parse({ ...raw, reuse });

    // then
    expect(findingText("INTEREST_WITHOUT_PAYOFF", snapshot)).toContain(
      "18 saved",
    );
  });

  it("phrases on-rhythm", () => {
    expect(
      findingText("ON_RHYTHM", healthSnapshotSchema.parse({ ...raw, reuse })),
    ).toMatch(/rhythm/i);
  });
});
