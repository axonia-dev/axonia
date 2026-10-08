import type { HealthSnapshot } from "@/features/health/domain/health";
import { TrendChip } from "@/features/health/ui/TrendChip";
import { pluralWord } from "@/lib/plural";

type Reach = HealthSnapshot["reach"];

const UNAVAILABLE_COPY =
  "Unavailable: no org directory is synced, so how far this reaches across the org cannot be read honestly.";

function Heading() {
  return (
    <h3 className="text-muted-foreground text-xs font-semibold tracking-wide uppercase">
      Cross-team reach
    </h3>
  );
}

/** NARROW_REACH fires at exactly one team, so "1 teams" would sit right above the finding saying so. */
function ReachStat({ value, noun }: Readonly<{ value: number; noun: string }>) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-foreground text-2xl font-semibold">{value}</span>
      <span className="text-muted-foreground text-xs">
        {pluralWord(value, noun)}
      </span>
    </div>
  );
}

/**
 * "Unavailable" renders as a dashed, unfilled card rather than a zero-filled one: an unsynced org
 * directory and a genuinely single-team community must never look the same.
 */
export function CrossTeamReachCard({ reach }: Readonly<{ reach: Reach }>) {
  if (!reach.available) {
    return (
      <article className="border-border-strong rounded-md border border-dashed p-4">
        <Heading />
        <p className="text-muted-foreground mt-2 text-sm">{UNAVAILABLE_COPY}</p>
      </article>
    );
  }
  const partial = reach.contributorsWithUnit < reach.totalContributors;
  return (
    <article className="border-border bg-surface rounded-md border p-4">
      <Heading />
      <div className="mt-2 flex flex-wrap items-start gap-6">
        <div className="flex items-center gap-2">
          <ReachStat value={reach.teams} noun="team" />
          <TrendChip direction={reach.trend.direction}>
            from {reach.trend.previous}
          </TrendChip>
        </div>
        <ReachStat value={reach.departments} noun="department" />
        <ReachStat value={reach.businessUnits} noun="business unit" />
      </div>
      {partial && (
        <p className="text-muted-foreground mt-3 text-xs">
          {reach.contributorsWithUnit} of {reach.totalContributors} contributors
          with team data
        </p>
      )}
    </article>
  );
}
