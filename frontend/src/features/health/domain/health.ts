import { z } from "zod";

import { plural } from "@/lib/plural";

const cadenceStatusSchema = z.enum(["STEADY", "SLOWING", "STALLED", "DORMANT"]);
export type CadenceStatus = z.infer<typeof cadenceStatusSchema>;

const trendDirectionSchema = z.enum(["UP", "DOWN", "FLAT"]);
export type TrendDirection = z.infer<typeof trendDirectionSchema>;

const trendSchema = z.object({
  previous: z.number(),
  direction: trendDirectionSchema,
});

export const healthSnapshotSchema = z.object({
  window: z.string(),
  generatedAt: z.string(),
  busFactor: z.number(),
  concentration: z.object({
    top3Share: z.number(),
    distribution: z.array(z.number()),
  }),
  cadence: z.object({
    status: cadenceStatusSchema,
    sessionsHeld: z.number(),
    medianGapDays: z.number().nullable(),
    trend: trendSchema,
  }),
  participation: z.object({
    activeMembers: z.number(),
    totalMembers: z.number(),
    share: z.number(),
    trend: trendSchema,
  }),
  engagement: z.object({
    loves: z.number(),
    trend: trendSchema,
  }),
  membershipGrowth: z.object({
    joins: z.number(),
    memberCount: z.number(),
    trend: trendSchema,
  }),
  reach: z.object({
    available: z.boolean(),
    teams: z.number(),
    departments: z.number(),
    businessUnits: z.number(),
    contributorsWithUnit: z.number(),
    totalContributors: z.number(),
    trend: trendSchema,
  }),
  reuse: z.object({
    opened: z.number(),
    artifactsOpened: z.number(),
    openingsTrend: trendSchema,
    saves: z.number(),
    artifactsSaved: z.number(),
    helped: z.number(),
    notHelped: z.number(),
    savesTrend: trendSchema,
  }),
  series: z.object({
    contributors: z.array(z.number()),
    participation: z.array(z.number()),
    engagement: z.array(z.number()),
    joins: z.array(z.number()),
    openings: z.array(z.number()),
    saves: z.array(z.number()),
  }),
  heldSessionDates: z.array(z.string()),
  diagnosis: z.object({
    findings: z.array(
      z.object({
        kind: z.enum(["STRENGTH", "RISK", "GAP"]),
        code: z.enum([
          "SINGLE_POINT",
          "OVER_RELIANCE",
          "FADING",
          "DORMANT",
          "SOCIAL_CLUB_DRIFT",
          "INTEREST_WITHOUT_PAYOFF",
          "NARROW_REACH",
          "WELL_DISTRIBUTED",
          "ON_RHYTHM",
          "KNOWLEDGE_REUSED",
        ]),
      }),
    ),
  }),
});
export type HealthSnapshot = z.infer<typeof healthSnapshotSchema>;

/** Advisory framing of a bus factor. */
export function busFactorRead(busFactor: number): string {
  if (busFactor === 0) return "No activity";
  if (busFactor === 1) return "Fragile";
  if (busFactor === 2) return "Concentrated";
  if (busFactor <= 4) return "Shared";
  return "Well-distributed";
}

const SINGLE_POINT_NUDGE = "Everything leans on one person; spread the load.";

/**
 * A word or two per status, never a sentence. Sentences are what findings are for, and a card that
 * states one restates the finding beneath it: that is the duplication this page was rebuilt to
 * delete. Keeping these too short to be a sentence makes the collision impossible by construction.
 */
export const CADENCE_WORD: Record<CadenceStatus, string> = {
  STEADY: "On rhythm",
  SLOWING: "Slowing",
  STALLED: "Stalled",
  DORMANT: "Dormant",
};

/**
 * What the window contained, stated tense-neutrally. "One session every 14 days" claims a rhythm
 * that is still running, which is a lie for a dormant community carrying an old median; a median gap
 * is a fact about the past. Null when there is no gap to state: the count, the pulse and the status
 * word already carry it, and inventing a sentence there is how the duplication came back.
 */
export function cadenceEvidence(
  cadence: HealthSnapshot["cadence"],
): string | null {
  const { medianGapDays } = cadence;
  if (medianGapDays === null) return null;
  // The median, never every session: a community with gaps of 0.5, 0.5, 30 and 40 days
  // has a median of 0 and a fortnight of silence, and "sessions landed a day apart"
  // would be a claim about all four.
  if (medianGapDays === 0) return "Median gap under a day between sessions.";
  return `Median gap of ${plural(medianGapDays, "day")} between sessions.`;
}

export function formatShare(share: number): string {
  return `${Math.round(share * 100)}%`;
}

export type Finding = HealthSnapshot["diagnosis"]["findings"][number];
export type FindingKind = Finding["kind"];
export type FindingCode = Finding["code"];

const FINDING_TEXT: Record<FindingCode, (s: HealthSnapshot) => string> = {
  SINGLE_POINT: () => SINGLE_POINT_NUDGE,
  OVER_RELIANCE: (s) =>
    `Leans on 2 people; ${formatShare(s.concentration.top3Share)} of contributions come from the top few.`,
  FADING: () =>
    "Losing momentum: cadence is slipping and fewer people are taking part.",
  DORMANT: () => "No active series: the community is dormant.",
  SOCIAL_CLUB_DRIFT: () =>
    "Plenty of engagement, but little is being produced or confirmed helpful.",
  INTEREST_WITHOUT_PAYOFF: (s) =>
    `Interest without confirmed payoff: ${s.reuse.saves} saved, but ${s.reuse.helped} said it helped.`,
  NARROW_REACH: () => "Contribution is drawn from a single team.",
  WELL_DISTRIBUTED: () => "Contribution is well spread across the community.",
  ON_RHYTHM: () => "Sessions are happening on rhythm.",
  KNOWLEDGE_REUSED: (s) =>
    `Knowledge is being reused: ${s.reuse.helped} confirmed it helped.`,
};

export function findingText(
  code: FindingCode,
  snapshot: HealthSnapshot,
): string {
  return FINDING_TEXT[code](snapshot);
}
