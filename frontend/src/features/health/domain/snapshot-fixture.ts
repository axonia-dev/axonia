import type { HealthSnapshot } from "@/features/health/domain/health";

const TALK_COUNTS = [2, 2, 2, 2, 1, 1, 1, 1, 1];
const TALKS = TALK_COUNTS.reduce((sum, count) => sum + count, 0);

const NO_FEEDBACK: HealthSnapshot["learning"] = {
  enjoyed: null,
  learned: null,
  answers: 0,
  enjoyedAnswers: 0,
  learnedAnswers: 0,
  sessions: 0,
  sessionsTooFew: 0,
  previousEnjoyed: null,
  previousLearned: null,
  status: { code: "LEARNING_TOO_FEW", level: "QUIET" },
};

/** The design's room that learned: 42 answers across 6 sessions, learning up and enjoyment level. */
export const learningFixture: HealthSnapshot["learning"] = {
  enjoyed: 8.7,
  learned: 8.3,
  answers: 42,
  enjoyedAnswers: 42,
  learnedAnswers: 42,
  sessions: 6,
  sessionsTooFew: 1,
  previousEnjoyed: 8.7,
  previousLearned: 7.6,
  status: { code: "LEARNING_A_LOT", level: "GREAT" },
};

/** The spec's healthy community: about monthly, nine speakers from seven teams, nothing paid off yet. */
export function snapshotFixture(
  overrides: Partial<HealthSnapshot> = {},
): HealthSnapshot {
  return {
    window: "90d",
    generatedAt: "2026-11-09T12:00:00Z",
    stage: {
      talks: TALKS,
      speakers: TALK_COUNTS.length,
      speakersForHalf: 4,
      distribution: TALK_COUNTS.map((count) => count / TALKS),
      trend: { previous: 6, direction: "UP" },
      status: { code: "SHARED_STAGE", level: "GREAT" },
    },
    curation: { acts: 10, topShare: 0.9 },
    cadence: {
      rhythm: "STEADY",
      sessionsHeld: 3,
      medianGapDays: 32,
      nextSessionAt: "2026-12-04T17:00:00Z",
      lastHeldAt: "2026-10-28T17:00:00Z",
      trend: { previous: 3, direction: "FLAT" },
      status: { code: "ON_RHYTHM", level: "GOOD" },
    },
    people: {
      involved: 15,
      members: 2,
      trend: { previous: 11, direction: "UP" },
    },
    engagement: { loves: 1, trend: { previous: 0, direction: "UP" } },
    membershipGrowth: {
      joins: 2,
      memberCount: 2,
      trend: { previous: 0, direction: "UP" },
    },
    reach: {
      available: true,
      teams: 7,
      departments: 3,
      businessUnits: 2,
      speakersWithUnit: 9,
      totalSpeakers: 9,
      trend: { previous: 5, direction: "UP" },
    },
    reuse: {
      opened: 2,
      artifactsOpened: 2,
      openingsTrend: { previous: 0, direction: "UP" },
      saves: 0,
      artifactsSaved: 0,
      helped: 0,
      notHelped: 0,
      savesTrend: { previous: 0, direction: "FLAT" },
      status: { code: "NO_PAYOFF_YET", level: "QUIET" },
    },
    learning: NO_FEEDBACK,
    payoff: { code: "NO_PAYOFF_YET", level: "QUIET" },
    series: {
      talks: [0, 5, 0, 0, 0, 0, 4, 0, 0, 0, 4, 0],
      people: [2, 8, 3, 2, 1, 2, 7, 3, 2, 4, 6, 5],
      joins: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1],
    },
    heldSessionDates: [
      "2026-08-25T17:00:00Z",
      "2026-09-26T17:00:00Z",
      "2026-10-28T17:00:00Z",
    ],
    diagnosis: { findings: [] },
    ...overrides,
  };
}

/** The spec's community in trouble: one session, one voice, nothing opened. */
export function troubledSnapshotFixture(): HealthSnapshot {
  const healthy = snapshotFixture();
  return {
    ...healthy,
    stage: {
      talks: 3,
      speakers: 1,
      speakersForHalf: 1,
      distribution: [1],
      trend: { previous: 3, direction: "DOWN" },
      status: { code: "ONE_VOICE", level: "WORRY" },
    },
    curation: { acts: 0, topShare: 0 },
    cadence: {
      rhythm: "SLOWING",
      sessionsHeld: 1,
      medianGapDays: null,
      nextSessionAt: null,
      lastHeldAt: "2026-09-09T10:00:00Z",
      trend: { previous: 4, direction: "DOWN" },
      status: { code: "SLOWING", level: "WATCH" },
    },
    people: {
      involved: 4,
      members: 4,
      trend: { previous: 11, direction: "DOWN" },
    },
    engagement: { loves: 0, trend: { previous: 2, direction: "DOWN" } },
    membershipGrowth: {
      joins: 0,
      memberCount: 6,
      trend: { previous: 1, direction: "DOWN" },
    },
    reach: {
      ...healthy.reach,
      teams: 1,
      departments: 1,
      businessUnits: 1,
      speakersWithUnit: 1,
      totalSpeakers: 1,
      trend: { previous: 3, direction: "DOWN" },
    },
    reuse: {
      opened: 0,
      artifactsOpened: 0,
      openingsTrend: { previous: 0, direction: "FLAT" },
      saves: 0,
      artifactsSaved: 0,
      helped: 0,
      notHelped: 0,
      savesTrend: { previous: 0, direction: "FLAT" },
      status: { code: "NOTHING_OPENED", level: "QUIET" },
    },
    payoff: { code: "NOTHING_OPENED", level: "QUIET" },
    series: {
      talks: [0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 0],
      people: [1, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0],
      joins: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    },
    heldSessionDates: ["2026-09-09T10:00:00Z"],
    diagnosis: {
      findings: [
        { kind: "RISK", code: "SINGLE_POINT" },
        { kind: "RISK", code: "FADING" },
      ],
    },
  };
}
