import type { HealthSnapshot } from "@/features/health/domain/health";
import { Fact } from "@/features/health/ui/Panel";
import { TrendNote } from "@/features/health/ui/TrendNote";

type Reuse = HealthSnapshot["reuse"];

const SMALLEST_VISIBLE_FILL = 4;

interface Step {
  label: string;
  count: number;
  trend?: Reuse["openingsTrend"];
}

/** Confirmed helpful has no trend: it is the claim the funnel narrows to, not a flow. */
function stepsOf(reuse: Reuse): Step[] {
  return [
    {
      label: "opened from the Library",
      count: reuse.opened,
      trend: reuse.openingsTrend,
    },
    {
      label: "saved to come back to",
      count: reuse.saves,
      trend: reuse.savesTrend,
    },
    { label: "confirmed it helped", count: reuse.helped },
  ];
}

/**
 * Three steps, widest first, each with a bar sized against the widest reading. The readings stay
 * apart and are never summed: being opened says the knowledge was reached for and nothing more,
 * and only helped carries the claim that it was applied.
 */
export function ReuseFunnel({ reuse }: Readonly<{ reuse: Reuse }>) {
  // The widest reading, not the first one: saves outrunning openings is possible across a window
  // boundary, and dividing by openings would then run a fill past its own track.
  const widest = Math.max(reuse.opened, reuse.saves, reuse.helped, 1);
  return (
    <ol className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-3 sm:gap-0">
      {stepsOf(reuse).map(({ label, count, trend }) => {
        const fill =
          count > 0
            ? Math.max((count / widest) * 100, SMALLEST_VISIBLE_FILL)
            : 0;
        return (
          <li
            key={label}
            className="border-border min-w-0 sm:border-l sm:px-[18px] sm:first:border-l-0 sm:first:pl-0"
          >
            <Fact
              value={count}
              trend={trend && <TrendNote current={count} trend={trend} />}
              caption={label}
            >
              <div className="bg-surface-sunken mt-2.5 mb-2 h-2.5 overflow-hidden rounded-[5px]">
                <div
                  className="bg-data-1 h-full rounded-[5px]"
                  style={{ width: `${fill}%` }}
                />
              </div>
            </Fact>
          </li>
        );
      })}
    </ol>
  );
}
