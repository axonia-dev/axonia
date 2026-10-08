import type { HealthSnapshot } from "@/features/health/domain/health";
import { Sparkline } from "@/features/health/ui/Sparkline";
import { TrendChip } from "@/features/health/ui/TrendChip";
import { plural } from "@/lib/plural";

interface Row {
  label: string;
  count: number;
  note: string;
  trend?: HealthSnapshot["reuse"]["savesTrend"];
  series?: { values: number[]; label: string };
}

function rowsOf(
  reuse: HealthSnapshot["reuse"],
  openings: number[],
  saves: number[],
): Row[] {
  return [
    {
      label: "opened",
      count: reuse.opened,
      note: `${plural(reuse.artifactsOpened, "artifact")} reached for`,
      trend: reuse.openingsTrend,
      series: { values: openings, label: "openings per bucket" },
    },
    {
      label: "saved",
      count: reuse.saves,
      note: `${plural(reuse.artifactsSaved, "artifact")} somebody wanted back`,
      trend: reuse.savesTrend,
      series: { values: saves, label: "saves per bucket" },
    },
    // Confirmed helpful has no trend and no series: it is the claim the funnel narrows to, not a
    // flow, and drawing it a shape would invent movement the backend never computed.
    {
      label: "confirmed helpful",
      count: reuse.helped,
      note: "said it actually helped",
    },
  ];
}

/**
 * The funnel widest first, drawn as an actual narrowing. The three readings stay apart and are never
 * summed: being opened says the knowledge was reached for and nothing more, and only helped carries
 * the claim that it was applied.
 */
export function ReuseFunnel({
  reuse,
  openings,
  saves,
}: Readonly<{
  reuse: HealthSnapshot["reuse"];
  openings: number[];
  saves: number[];
}>) {
  const rows = rowsOf(reuse, openings, saves);
  // The widest reading, not the first one: saves outrunning openings is arithmetically possible
  // across a window boundary, and dividing by openings would then run a fill past its own track.
  const widest = Math.max(reuse.opened, reuse.saves, reuse.helped, 1);

  return (
    <ul className="flex flex-col">
      {rows.map((row, index) => {
        // Zero at index 0, which is what keeps the drop line off the first row.
        const previous = rows[index - 1]?.count ?? 0;
        const fill = Math.max(
          (row.count / widest) * 100,
          row.count > 0 ? 4 : 0,
        );
        return (
          <li key={row.label} className="flex flex-col">
            {previous > 0 && (
              <p className="text-muted-foreground py-0.5 pl-[216px] text-xs max-sm:pl-0">
                {Math.round((row.count / previous) * 100)}% of the step above
              </p>
            )}
            <div className="grid grid-cols-[200px_minmax(0,1fr)_92px] items-center gap-x-4 py-2 max-sm:grid-cols-1">
              <div>
                <span className="font-display text-xl font-semibold">
                  {row.count}
                </span>{" "}
                <span className="text-sm font-semibold">{row.label}</span>
                <span className="text-muted-foreground block text-xs">
                  {row.note}
                </span>
                {/* Beside the count and not on the track: a shape drawn under the track would sit
                    between two fills and read as part of the narrowing rather than as time. */}
                {row.series && (
                  <Sparkline
                    values={row.series.values}
                    label={row.series.label}
                  />
                )}
              </div>
              <div className="bg-surface-sunken relative h-9 overflow-hidden rounded-md">
                <div
                  className="absolute inset-y-0 left-0 rounded-md"
                  style={{
                    width: `${fill}%`,
                    background: `linear-gradient(90deg, var(--primary), color-mix(in srgb, var(--primary) 58%, var(--surface)))`,
                    opacity: 1 - index * 0.2,
                  }}
                />
              </div>
              {row.trend && (
                <TrendChip direction={row.trend.direction}>
                  from {row.trend.previous}
                </TrendChip>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );
}
