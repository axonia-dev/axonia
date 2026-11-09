import type { HealthSnapshot } from "@/features/health/domain/health";
import { STATUS_WORD } from "@/features/health/domain/status";

const RHYTHMS: ReadonlyArray<{ upToDays: number; word: string }> = [
  { upToDays: 10, word: "About weekly" },
  { upToDays: 21, word: "About every 2 weeks" },
  { upToDays: 45, word: "About monthly" },
];

/** The rhythm in words when the window has a median gap, the status word when it has none. */
export function rhythmWord(
  cadence: Pick<HealthSnapshot["cadence"], "medianGapDays" | "status">,
): string {
  const { medianGapDays, status } = cadence;
  if (medianGapDays === null) return STATUS_WORD[status.code];
  return (
    RHYTHMS.find(({ upToDays }) => medianGapDays <= upToDays)?.word ??
    "Every few months"
  );
}
