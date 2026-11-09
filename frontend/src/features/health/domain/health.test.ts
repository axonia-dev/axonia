import { describe, expect, it } from "vitest";

import { CHAPTER_OF } from "@/features/health/domain/finding-routing";
import type { FindingCode } from "@/features/health/domain/health";
import {
  findingText,
  formatShare,
  healthSnapshotSchema,
} from "@/features/health/domain/health";
import { snapshotFixture } from "@/features/health/domain/snapshot-fixture";
import { STATUS_WORD } from "@/features/health/domain/status";

const raw = {
  window: "90d",
  generatedAt: "2026-11-09T12:00:00Z",
  stage: {
    talks: 13,
    speakers: 9,
    speakersForHalf: 4,
    distribution: [0.16, 0.16, 0.16, 0.16, 0.08, 0.08, 0.08, 0.08, 0.04],
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
  diagnosis: {
    findings: [{ kind: "RISK", code: "CURATION_LEANS_ON_ONE" }],
  },
};

describe("healthSnapshotSchema", () => {
  it("parses the backend's snapshot", () => {
    // when
    const actual = healthSnapshotSchema.parse(raw);

    // then
    expect(actual.stage.speakersForHalf).toBe(4);
    expect(actual.cadence.status).toEqual({
      code: "ON_RHYTHM",
      level: "GOOD",
    });
    expect(actual.people.members).toBe(2);
    expect(actual.series.talks).toHaveLength(12);
    expect(actual.diagnosis.findings[0]?.code).toBe("CURATION_LEANS_ON_ONE");
  });

  it("accepts no median gap, no next session and no session ever held", () => {
    // when
    const actual = healthSnapshotSchema.parse({
      ...raw,
      cadence: {
        ...raw.cadence,
        medianGapDays: null,
        nextSessionAt: null,
        lastHeldAt: null,
      },
    });

    // then
    expect(actual.cadence.medianGapDays).toBeNull();
    expect(actual.cadence.nextSessionAt).toBeNull();
    expect(actual.cadence.lastHeldAt).toBeNull();
  });

  it("refuses the retired participation shape", () => {
    // given
    const retired = {
      ...raw,
      people: undefined,
      participation: { activeMembers: 15, totalMembers: 2, share: 7.5 },
    };

    // when
    const actual = healthSnapshotSchema.safeParse(retired);

    // then
    expect(actual.success).toBe(false);
  });

  it("keeps the fixture in the shape the schema parses", () => {
    // when
    const actual = healthSnapshotSchema.safeParse(snapshotFixture());

    // then
    expect(actual.success).toBe(true);
  });
});

describe("formatShare", () => {
  it("renders a 0..1 share as a percentage", () => {
    expect(formatShare(0.4)).toBe("40%");
  });
});

describe("findingText", () => {
  const snapshot = healthSnapshotSchema.parse(raw);

  it.each<[FindingCode, string]>([
    ["SINGLE_POINT", "Every talk came from one person."],
    ["OVER_RELIANCE", "One speaker gave most of the talks."],
    [
      "CURATION_LEANS_ON_ONE",
      "Curation leans on one person: 90% of topics and Library additions.",
    ],
    ["NARROW_REACH", "Every speaker comes from one team."],
    ["WELL_DISTRIBUTED", "The stage is shared across 9 speakers from 7 teams."],
    ["DORMANT", "No sessions held and none scheduled."],
    ["FADING", "Fewer sessions and fewer people than the period before."],
    [
      "SOCIAL_CLUB_DRIFT",
      "Plenty of engagement, but no talks and nothing confirmed helpful.",
    ],
    ["ON_RHYTHM", "Sessions keep a steady rhythm."],
    ["INTEREST_WITHOUT_PAYOFF", "0 saved, none confirmed helpful yet."],
    ["KNOWLEDGE_REUSED", "Knowledge is being reused: 0 confirmed it helped."],
  ])("phrases %s", (code, text) => {
    // when
    const actual = findingText(code, snapshot);

    // then
    expect(actual).toBe(text);
  });

  it("names no teams in a shared stage when reach is unavailable", () => {
    // given
    const unsynced = {
      ...snapshot,
      reach: { ...snapshot.reach, available: false },
    };

    // when
    const actual = findingText("WELL_DISTRIBUTED", unsynced);

    // then
    expect(actual).toBe("The stage is shared across 9 speakers.");
  });

  it("names no teams in a shared stage held by one team, which NARROW_REACH states", () => {
    // given
    const oneTeam = {
      ...snapshot,
      reach: { ...snapshot.reach, teams: 1 },
    };

    // when
    const actual = findingText("WELL_DISTRIBUTED", oneTeam);

    // then
    expect(actual).toBe("The stage is shared across 9 speakers.");
  });

  it("can never collide with a status word, because no finding is that short", () => {
    const words = Object.values(STATUS_WORD);

    for (const code of Object.keys(CHAPTER_OF) as FindingCode[]) {
      expect(words).not.toContain(findingText(code, snapshot));
    }
  });
});
