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

export type ActKind = "session" | "addition" | "join";

export interface Act {
  readonly kind: ActKind;
  readonly daysAgo: number;
}

export const VISIBLE_CAP = 44;
const SATURATION = 22;
const ALWAYS_SHOWN = 3;

function actsOf(series: readonly number[], kind: ActKind): Act[] {
  return series.flatMap((perDay, index) =>
    Array.from({ length: perDay }, () => ({
      kind,
      daysAgo: series.length - 1 - index,
    })),
  );
}

/** One act per count in the trace, freshest first. */
export function toActs(trace: ActivityTrace): Act[] {
  const acts = [
    ...actsOf(trace.sessionsHeld, "session"),
    ...actsOf(trace.libraryAdditions, "addition"),
    ...actsOf(trace.joins, "join"),
  ];
  acts.sort((left, right) => left.daysAgo - right.daysAgo);
  return acts;
}

/**
 * The acts that get a light: the freshest, as many as a saturating curve allows, so a busy
 * community reads as a full sky rather than a blizzard. Never more lights than acts.
 */
export function visibleActs(acts: readonly Act[]): Act[] {
  const curve = Math.round(
    VISIBLE_CAP * (1 - Math.exp(-acts.length / SATURATION)),
  );
  const shown = Math.max(curve, Math.min(acts.length, ALWAYS_SHOWN));
  return acts.slice(0, Math.min(shown, acts.length));
}
