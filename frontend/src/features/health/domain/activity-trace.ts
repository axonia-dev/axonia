import { z } from "zod";

const count = z.number().int().nonnegative();

export const activityTraceSchema = z
  .object({
    days: z.number().int().positive(),
    sessionsHeld: z.array(count),
    libraryAdditions: z.array(count),
    joins: z.array(count),
  })
  .refine(
    (trace) =>
      [trace.sessionsHeld, trace.libraryAdditions, trace.joins].every(
        (series) => series.length === trace.days,
      ),
    { message: "every series holds one count per day" },
  );

export type ActivityTrace = z.infer<typeof activityTraceSchema>;

type ActKind = "session" | "addition" | "join";

export interface Act {
  readonly kind: ActKind;
  readonly daysAgo: number;
  /** Which of that day's acts of that kind this is: a stable name for the act. */
  readonly ordinal: number;
}

export const VISIBLE_CAP = 44;
const SATURATION = 22;
const ALWAYS_SHOWN = 3;

const seriesOf = (trace: ActivityTrace) =>
  [
    ["session", trace.sessionsHeld],
    ["addition", trace.libraryAdditions],
    ["join", trace.joins],
  ] as const;

const totalOf = (trace: ActivityTrace) =>
  seriesOf(trace)
    .flatMap(([, series]) => series)
    .reduce((total, perDay) => total + perDay, 0);

/** One act per count in the trace, freshest first, stopping at `limit`. */
export function toActs(
  trace: ActivityTrace,
  limit = Number.POSITIVE_INFINITY,
): Act[] {
  const acts: Act[] = [];
  const kinds = seriesOf(trace);
  for (let daysAgo = 0; daysAgo < trace.days; daysAgo++) {
    for (const [kind, series] of kinds) {
      const perDay = series.at(trace.days - 1 - daysAgo) ?? 0;
      const take = Math.min(perDay, limit - acts.length);
      for (let ordinal = 0; ordinal < take; ordinal++) {
        acts.push({ kind, daysAgo, ordinal });
      }
    }
  }
  return acts;
}

/** How many of `total` acts get a light: a saturating curve, never more than there are acts. */
function shownOf(total: number): number {
  const curve = Math.round(VISIBLE_CAP * (1 - Math.exp(-total / SATURATION)));
  return Math.min(Math.max(curve, Math.min(total, ALWAYS_SHOWN)), total);
}

/**
 * The acts that get a light: the freshest, as many as a saturating curve allows, so a busy
 * community reads as a full sky rather than a blizzard. Never more lights than acts.
 */
export function visibleActs(acts: readonly Act[]): Act[] {
  return acts.slice(0, shownOf(acts.length));
}

/** `visibleActs(toActs(trace))`, without one act per count for a community that counts thousands. */
export function visibleActsOf(trace: ActivityTrace): Act[] {
  return toActs(trace, shownOf(totalOf(trace)));
}
