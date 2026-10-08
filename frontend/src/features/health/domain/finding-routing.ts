import type { Finding, FindingCode } from "@/features/health/domain/health";

export type Chapter = "carried" | "alive" | "payoff";

/**
 * Each finding renders inside the chapter that shows the evidence for it, which is what stops a
 * finding being stated twice: there is only one place it can be stated.
 */
export const CHAPTER_OF: Record<FindingCode, Chapter> = {
  SINGLE_POINT: "carried",
  OVER_RELIANCE: "carried",
  WELL_DISTRIBUTED: "carried",
  NARROW_REACH: "carried",
  FADING: "alive",
  DORMANT: "alive",
  ON_RHYTHM: "alive",
  SOCIAL_CLUB_DRIFT: "alive",
  KNOWLEDGE_REUSED: "payoff",
  INTEREST_WITHOUT_PAYOFF: "payoff",
};

export function findingsFor(chapter: Chapter, findings: Finding[]): Finding[] {
  return findings.filter((finding) => CHAPTER_OF[finding.code] === chapter);
}
