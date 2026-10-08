import type { HealthSnapshot } from "@/features/health/domain/health";

const TWELVE = [1, 0, 2, 0, 0, 1, 0, 0, 3, 0, 0, 1];

export function snapshotFixture(
  overrides: Partial<HealthSnapshot> = {},
): HealthSnapshot {
  return {
    window: "90d",
    generatedAt: "2026-06-11T12:00:00Z",
    busFactor: 4,
    concentration: { top3Share: 0.55, distribution: [0.45, 0.25, 0.2, 0.1] },
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
      saves: 9,
      artifactsSaved: 5,
      helped: 3,
      notHelped: 1,
      savesTrend: { previous: 7, direction: "UP" },
    },
    series: {
      contributors: [...TWELVE],
      participation: [...TWELVE],
      engagement: [...TWELVE],
      joins: [...TWELVE],
      openings: [...TWELVE],
      saves: [...TWELVE],
    },
    heldSessionDates: ["2026-04-02T17:00:00Z", "2026-05-14T17:00:00Z"],
    diagnosis: { findings: [] },
    ...overrides,
  };
}
