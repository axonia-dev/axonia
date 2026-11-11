import { z } from "zod";

import { formatAverage } from "@/components/patterns/average-bars/format-average";
import { healthStatusSchema } from "@/features/health/domain/status";
import { plural } from "@/lib/plural";

const cadenceStatusSchema = z.enum(["STEADY", "SLOWING", "STALLED", "DORMANT"]);

const trendDirectionSchema = z.enum(["UP", "DOWN", "FLAT"]);

const trendSchema = z.object({
  previous: z.number(),
  direction: trendDirectionSchema,
});

export const healthSnapshotSchema = z.object({
  window: z.string(),
  generatedAt: z.string(),
  stage: z.object({
    talks: z.number(),
    speakers: z.number(),
    speakersForHalf: z.number(),
    distribution: z.array(z.number()),
    trend: trendSchema,
    status: healthStatusSchema,
  }),
  curation: z.object({
    acts: z.number(),
    topShare: z.number(),
  }),
  cadence: z.object({
    rhythm: cadenceStatusSchema,
    sessionsHeld: z.number(),
    medianGapDays: z.number().nullable(),
    nextSessionAt: z.string().nullable(),
    lastHeldAt: z.string().nullable(),
    trend: trendSchema,
    status: healthStatusSchema,
  }),
  people: z.object({
    involved: z.number(),
    members: z.number(),
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
    speakersWithUnit: z.number(),
    totalSpeakers: z.number(),
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
    status: healthStatusSchema,
  }),
  learning: z.object({
    enjoyed: z.number().nullable(),
    learned: z.number().nullable(),
    answers: z.number(),
    enjoyedAnswers: z.number(),
    learnedAnswers: z.number(),
    sessions: z.number(),
    sessionsTooFew: z.number(),
    previousEnjoyed: z.number().nullable(),
    previousLearned: z.number().nullable(),
    status: healthStatusSchema,
  }),
  payoff: healthStatusSchema,
  series: z.object({
    talks: z.array(z.number()),
    people: z.array(z.number()),
    joins: z.array(z.number()),
  }),
  heldSessionDates: z.array(z.string()),
  diagnosis: z.object({
    findings: z.array(
      z.object({
        kind: z.enum(["STRENGTH", "RISK", "GAP"]),
        code: z.enum([
          "SINGLE_POINT",
          "OVER_RELIANCE",
          "CURATION_LEANS_ON_ONE",
          "FADING",
          "DORMANT",
          "SOCIAL_CLUB_DRIFT",
          "INTEREST_WITHOUT_PAYOFF",
          "NARROW_REACH",
          "WELL_DISTRIBUTED",
          "ON_RHYTHM",
          "KNOWLEDGE_REUSED",
          "LEARNING_LANDS",
          "ENJOYED_NOT_LEARNED",
          "SESSIONS_LAND_POORLY",
        ]),
      }),
    ),
  }),
});
export type HealthSnapshot = z.infer<typeof healthSnapshotSchema>;

export type Trend = z.infer<typeof trendSchema>;

export function formatShare(share: number): string {
  return `${Math.round(share * 100)}%`;
}

export type Finding = HealthSnapshot["diagnosis"]["findings"][number];
export type FindingKind = Finding["kind"];
export type FindingCode = Finding["code"];

function stageShared(s: HealthSnapshot): string {
  const speakers = plural(s.stage.speakers, "speaker");
  if (!s.reach.available || s.reach.teams <= 1) {
    return `The stage is shared across ${speakers}.`;
  }
  return `The stage is shared across ${speakers} from ${plural(s.reach.teams, "team")}.`;
}

const FINDING_TEXT: Record<FindingCode, (s: HealthSnapshot) => string> = {
  SINGLE_POINT: () => "Every talk came from one person.",
  OVER_RELIANCE: () => "One speaker gave most of the talks.",
  CURATION_LEANS_ON_ONE: (s) =>
    `Curation leans on one person: ${formatShare(s.curation.topShare)} of topics and Library additions.`,
  NARROW_REACH: () => "Every speaker comes from one team.",
  WELL_DISTRIBUTED: stageShared,
  DORMANT: () => "No sessions held and none scheduled.",
  FADING: () => "Fewer sessions and fewer people than the period before.",
  SOCIAL_CLUB_DRIFT: () =>
    "Plenty of engagement, but no talks and nothing confirmed helpful.",
  ON_RHYTHM: () => "Sessions keep a steady rhythm.",
  INTEREST_WITHOUT_PAYOFF: (s) =>
    `${s.reuse.saves} saved, none confirmed helpful yet.`,
  KNOWLEDGE_REUSED: (s) =>
    `Knowledge is being reused: ${s.reuse.helped} confirmed it helped.`,
  LEARNING_LANDS: (s) =>
    `Attendees report learning a lot: ${formatAverage(s.learning.learned ?? 0)} out of 10.`,
  ENJOYED_NOT_LEARNED: () => "Sessions are enjoyed more than they teach.",
  SESSIONS_LAND_POORLY: (s) =>
    `Sessions are not landing: enjoyed ${formatAverage(s.learning.enjoyed ?? 0)} out of 10.`,
};

export function findingText(
  code: FindingCode,
  snapshot: HealthSnapshot,
): string {
  return FINDING_TEXT[code](snapshot);
}
