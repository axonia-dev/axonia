import type { Chapter } from "@/features/health/domain/finding-routing";
import type {
  CadenceStatus,
  HealthSnapshot,
} from "@/features/health/domain/health";
import { CADENCE_WORD } from "@/features/health/domain/health";
import { plural } from "@/lib/plural";

export type Tone = "danger" | "warning" | "success" | "neutral";

export interface Verdict {
  chapter: Chapter;
  label: string;
  state: string;
  note: string;
  tone: Tone;
}

/**
 * A state in two or three words per chapter, never a sentence and never a score. It exists so the
 * page has a glance-read; if it ever grows into sentences it has become the diagnosis panel this
 * design deleted, and it should be cut rather than trimmed.
 */
function carried(snapshot: HealthSnapshot): Verdict {
  const { busFactor } = snapshot;
  if (busFactor === 0) {
    return {
      chapter: "carried",
      label: "Carried by",
      state: "Nobody",
      note: "no contribution this window",
      tone: "danger",
    };
  }
  if (busFactor === 1) {
    return {
      chapter: "carried",
      label: "Carried by",
      state: "One person",
      note: "the whole community rests on them",
      tone: "warning",
    };
  }
  const stillConcentrated = busFactor === 2;
  return {
    chapter: "carried",
    label: "Carried by",
    state: plural(busFactor, "person"),
    note: stillConcentrated ? "still concentrated" : "the load is shared",
    tone: stillConcentrated ? "warning" : "success",
  };
}

/** The word itself lives in CADENCE_WORD, so the strip and the cadence card cannot drift apart. */
const CADENCE_TONE: Record<CadenceStatus, Tone> = {
  STEADY: "success",
  SLOWING: "warning",
  STALLED: "warning",
  DORMANT: "danger",
};

function alive(snapshot: HealthSnapshot): Verdict {
  const { status, sessionsHeld } = snapshot.cadence;
  return {
    chapter: "alive",
    label: "Alive",
    state: CADENCE_WORD[status],
    note:
      status === "DORMANT"
        ? "no active series"
        : `${plural(sessionsHeld, "session")} held`,
    tone: CADENCE_TONE[status],
  };
}

function payoff(snapshot: HealthSnapshot): Verdict {
  const { saves, helped } = snapshot.reuse;
  if (helped > 0) {
    return {
      chapter: "payoff",
      label: "Paying off",
      state: "Reused",
      note: `${helped} confirmed it helped`,
      tone: "success",
    };
  }
  if (saves > 0) {
    return {
      chapter: "payoff",
      label: "Paying off",
      state: "Wanted, unproven",
      note: "saved, but nobody has confirmed it helped",
      tone: "neutral",
    };
  }
  return {
    chapter: "payoff",
    label: "Paying off",
    state: "Nothing yet",
    note: "no captured knowledge has been reached for",
    tone: "neutral",
  };
}

export function verdictsOf(snapshot: HealthSnapshot): Verdict[] {
  return [carried(snapshot), alive(snapshot), payoff(snapshot)];
}
