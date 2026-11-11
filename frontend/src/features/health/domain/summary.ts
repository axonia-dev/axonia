import type { HealthSnapshot } from "@/features/health/domain/health";
import { rhythmWord } from "@/features/health/domain/rhythm";
import type { StatusCode } from "@/features/health/domain/status";
import { plural } from "@/lib/plural";

function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1);
}

const NOT_ON_RHYTHM: Partial<Record<StatusCode, string>> = {
  DORMANT: "*No sessions* are held or scheduled",
  STARTING_UP: "The *first session* is scheduled",
  STALLED: "Sessions have *stalled*",
  SLOWING: "Sessions are *slowing down*",
};

function aliveClause({ cadence }: HealthSnapshot): string {
  const { status, medianGapDays } = cadence;
  const notOnRhythm = NOT_ON_RHYTHM[status.code];
  if (notOnRhythm !== undefined) return notOnRhythm;
  if (medianGapDays !== null)
    return `Meeting *${lowerFirst(rhythmWord(cadence))}*`;
  return status.code === "PICKING_UP"
    ? "Sessions are *picking up*"
    : "Sessions are *on rhythm*";
}

function fewTalks(talks: number): string {
  if (talks === 0) return "*no talks* were given";
  return `only *${plural(talks, "talk")}* ${talks === 1 ? "was" : "were"} given`;
}

function sharedStage({ stage, reach }: HealthSnapshot): string {
  const speakers = `*${stage.speakers} different speakers*`;
  if (!reach.available) return speakers;
  return `${speakers} from ${plural(reach.teams, "team")}`;
}

function firstSentence(snapshot: HealthSnapshot): string {
  const alive = aliveClause(snapshot);
  switch (snapshot.stage.status.code) {
    case "ONE_VOICE":
      return `${alive} and *one person* gave every talk.`;
    case "LEANS_ON_ONE":
      return `${alive} and *one speaker* gave most of the talks.`;
    case "TOO_FEW_TALKS":
      return `${alive} and ${fewTalks(snapshot.stage.talks)}.`;
    default:
      return `${alive}, with ${sharedStage(snapshot)}.`;
  }
}

const PAYOFF_SENTENCE: Partial<Record<StatusCode, string>> = {
  PAYING_OFF: "Library knowledge is paying off.",
  STARTING_TO_PAY_OFF: "Library knowledge is starting to pay off.",
  SAVED_NOT_CONFIRMED:
    "Library knowledge is being saved but has not paid off yet.",
  NO_PAYOFF_YET: "Library knowledge is being opened but has not paid off yet.",
  LEARNING_A_LOT: "Attendees report *learning a lot*.",
  LEARNING: "Attendees report *learning*.",
  LITTLE_LEARNED: "Attendees report *little learned*.",
};

export interface SummaryPart {
  text: string;
  emphasised: boolean;
}

function partsOf(marked: string): SummaryPart[] {
  return marked
    .split("*")
    .map((text, index) => ({ text, emphasised: index % 2 === 1 }))
    .filter(({ text }) => text !== "");
}

/** The reuse reading, or what learning adds after it when reuse won the pill and the room spoke. */
function payoffClause({ payoff, reuse, learning }: HealthSnapshot): string {
  const lead =
    PAYOFF_SENTENCE[payoff.code] ??
    PAYOFF_SENTENCE[reuse.status.code] ??
    "Nothing from the Library has been opened.";
  const fromReuse = payoff.code !== learning.status.code;
  const learned =
    learning.status.level === "QUIET"
      ? undefined
      : PAYOFF_SENTENCE[learning.status.code];
  return fromReuse && learned !== undefined ? `${lead} ${learned}` : lead;
}

/**
 * A few short sentences composed from the statuses and their headline facts, with the phrases a
 * reader scans for emphasised. Deterministic, so the same snapshot always reads the same, and
 * never a score.
 */
export function summaryOf(snapshot: HealthSnapshot): SummaryPart[] {
  return partsOf(`${firstSentence(snapshot)} ${payoffClause(snapshot)}`);
}
