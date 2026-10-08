import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpHealthGateway } from "@/features/health/adapters/http-health-gateway";

const raw = {
  window: "90d",
  generatedAt: "2026-06-11T12:00:00Z",
  busFactor: 2,
  concentration: { top3Share: 0.8, distribution: [0.4, 0.28, 0.32] },
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
  diagnosis: { findings: [] },
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

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("httpHealthGateway", () => {
  it("fetches and parses the snapshot for a community and window", async () => {
    // given
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(raw), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    // when
    const actual = await httpHealthGateway.getSnapshot("backend-guild", "90d");

    // then
    expect(actual.busFactor).toBe(2);
    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining(
        "/axonia/communities/backend-guild/health?window=90d",
      ),
      expect.anything(),
    );
  });

  it("parses the series and the held session dates", async () => {
    // given
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify(raw), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
    );

    // when
    const snapshot = await httpHealthGateway.getSnapshot("ring", "90d");

    // then
    expect(snapshot.series.contributors).toHaveLength(12);
    expect(snapshot.series.saves).toEqual(raw.series.saves);
    expect(snapshot.heldSessionDates).toEqual(["2026-05-02T17:00:00Z"]);
  });
});
