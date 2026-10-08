import type { SelfArea, Tier } from "@/features/expertise/domain/expertise";

interface ExpertiseSummary {
  counts: Record<Tier, number>;
  totalSignals: number;
  hiddenCount: number;
  standing: string;
}

function areaWord(n: number): string {
  return n === 1 ? "area" : "areas";
}

function standingSentence(counts: Record<Tier, number>): string {
  const {
    PRACTITIONER: p,
    CONTRIBUTOR: c,
    INTERESTED: i,
    OPEN_TO_QUESTIONS: o,
  } = counts;

  if (p + c + i === 0) {
    return o > 0
      ? `Open to questions in ${o} ${areaWord(o)}.`
      : "Not yet represented in the expertise graph.";
  }
  if (p > 0) {
    const rest = c + i;
    const base = `Practitioner in ${p} ${areaWord(p)}`;
    return rest > 0 ? `${base} and growing in ${rest} more.` : `${base}.`;
  }
  if (c > 0) {
    const base = `Contributor in ${c} ${areaWord(c)}`;
    return i > 0 ? `${base}, exploring ${i} more.` : `${base}.`;
  }
  return `Exploring ${i} ${areaWord(i)}.`;
}

/** `openCount` is the number of declared areas no evidence shows yet. */
export function summarize(areas: SelfArea[], openCount = 0): ExpertiseSummary {
  const counts: Record<Tier, number> = {
    PRACTITIONER: 0,
    CONTRIBUTOR: 0,
    INTERESTED: 0,
    OPEN_TO_QUESTIONS: openCount,
  };
  let totalSignals = 0;
  let hiddenCount = 0;

  for (const area of areas) {
    counts[area.tier] += 1;
    totalSignals +=
      area.evidence.presentedCount +
      area.evidence.contributedCount +
      area.evidence.offeredCount;
    if (area.hidden) hiddenCount += 1;
  }

  return {
    counts,
    totalSignals,
    hiddenCount,
    standing: standingSentence(counts),
  };
}
