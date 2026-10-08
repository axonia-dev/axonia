import { busFactorRead } from "@/features/health/domain/health";
import { LoadBearingStack } from "@/features/health/ui/LoadBearingStack";
import { Sparkline } from "@/features/health/ui/Sparkline";

/**
 * The number, what it means, the trend behind it and the stack that draws its definition. The tone
 * is semantic and never the community accent: a community whose accent happens to be green must not
 * have a bus factor of 1 drawn in the colour of good news.
 */
export function ContributionSpreadCard({
  busFactor,
  distribution,
  contributors,
  tone,
}: Readonly<{
  busFactor: number;
  distribution: number[];
  contributors: number[];
  tone: string;
}>) {
  return (
    <article className="border-border bg-surface rounded-md border p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex items-baseline gap-3">
          <span
            className="font-display text-5xl leading-none font-bold"
            style={{ color: tone }}
          >
            {busFactor}
          </span>
          <span className="flex flex-col">
            <span className="text-foreground text-base font-semibold">
              {busFactorRead(busFactor)}
            </span>
            <span className="text-muted-foreground text-xs">bus factor</span>
          </span>
        </div>
        <div className="w-[150px] max-sm:w-full">
          <Sparkline values={contributors} label="contributors per bucket" />
        </div>
      </div>
      <LoadBearingStack distribution={distribution} tone={tone} />
    </article>
  );
}
