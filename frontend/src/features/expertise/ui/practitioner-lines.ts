import type {
  Evidence,
  Practitioner,
} from "@/features/expertise/domain/expertise";
import { monthName } from "@/lib/declared-on";

/** The one evidence line Search and the map's panel both print under a person. */
export function countSummary(evidence: Evidence): string {
  const parts: string[] = [];
  if (evidence.presentedCount > 0) {
    parts.push(
      `hosted ${evidence.presentedCount} ${evidence.presentedCount === 1 ? "session" : "sessions"}`,
    );
  }
  if (evidence.contributedCount > 0) {
    parts.push(
      `${evidence.contributedCount} ${evidence.contributedCount === 1 ? "artifact" : "artifacts"}`,
    );
  }
  if (evidence.offeredCount > 0) {
    parts.push(
      `offered ${evidence.offeredCount} ${evidence.offeredCount === 1 ? "topic" : "topics"}`,
    );
  }
  return parts.join(" · ");
}

/** The line under a person whose only claim on the term is a declaration. */
export function declarationLine(practitioner: Practitioner): string | null {
  if (
    !practitioner.openToQuestions ||
    practitioner.declaredAt === null ||
    practitioner.tier !== "OPEN_TO_QUESTIONS"
  ) {
    return null;
  }
  return `Declared in ${monthName(practitioner.declaredAt)}, no activity here yet`;
}
