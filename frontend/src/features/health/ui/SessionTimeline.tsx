import {
  dayMonth,
  fractionOf,
  windowDaysOf,
  windowSpan,
} from "@/features/health/domain/window";
import { plural } from "@/lib/plural";

const MARK = "absolute top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full";

/**
 * Each session really held, at its real date, and the next one as a dashed mark at the far end. A
 * steady rhythm reads as an even row, slowing as marks bunching left, dormant as an empty line.
 */
export function SessionTimeline({
  heldSessionDates,
  nextSessionAt,
  windowId,
  generatedAt,
}: Readonly<{
  heldSessionDates: string[];
  nextSessionAt: string | null;
  windowId: string;
  generatedAt: string;
}>) {
  const span = windowSpan(windowId, generatedAt);
  const held = heldSessionDates
    .map((date) => ({ date, fraction: fractionOf(span, date) }))
    .filter(({ fraction }) => fraction >= 0 && fraction <= 1);
  const next =
    nextSessionAt === null
      ? ""
      : `, the next on ${dayMonth(Date.parse(nextSessionAt))}`;
  const label = `${plural(held.length, "session")} held in the last ${windowDaysOf(windowId)} days${next}`;

  return (
    <div role="img" aria-label={label} className="relative mx-2 mt-2 h-9">
      <div className="bg-border-strong absolute inset-x-0 top-1/2 h-0.5 -translate-y-1/2 rounded-full" />
      {held.map(({ date, fraction }, index) => (
        <span
          key={`${date}-${index}`}
          data-session="held"
          style={{ left: `${fraction * 100}%` }}
          className={`${MARK} bg-data-1 size-3.5`}
        />
      ))}
      {nextSessionAt !== null && (
        <span
          data-session="next"
          style={{ left: "100%" }}
          className={`${MARK} border-data-1 bg-surface size-3 border-2 border-dashed`}
        />
      )}
    </div>
  );
}
