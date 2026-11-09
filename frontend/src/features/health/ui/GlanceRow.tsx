import type { HealthSnapshot } from "@/features/health/domain/health";
import { rhythmWord } from "@/features/health/domain/rhythm";
import { StatTile } from "@/features/health/ui/StatTile";
import { StatusPill } from "@/features/health/ui/StatusPill";

function membersLine({
  involved,
  members,
  trend,
}: HealthSnapshot["people"]): string {
  if (involved === 0) {
    return trend.previous === 0 ? "Nobody yet" : "Nobody this window";
  }
  if (members === 0) return "none of them members";
  if (members === involved) return "all members";
  if (members === 1) return "1 of them a member";
  return `${members} of them members`;
}

export function GlanceRow({
  snapshot,
}: Readonly<{ snapshot: HealthSnapshot }>) {
  const { cadence, stage, people, reuse } = snapshot;
  const members = membersLine(people);
  return (
    <div className="grid grid-cols-2 gap-3.5 md:grid-cols-4">
      <StatTile
        label="Sessions"
        value={cadence.sessionsHeld}
        trend={cadence.trend}
      >
        <StatusPill status={cadence.status} label={rhythmWord(cadence)} />
      </StatTile>
      <StatTile label="Speakers" value={stage.speakers} trend={stage.trend}>
        <StatusPill status={stage.status} />
      </StatTile>
      <StatTile
        label="People involved"
        value={people.involved}
        trend={people.trend}
      >
        <span className="text-muted-foreground text-xs">{members}</span>
      </StatTile>
      <StatTile
        label="Library openings"
        value={reuse.opened}
        trend={reuse.openingsTrend}
      >
        <StatusPill status={reuse.status} />
      </StatTile>
    </div>
  );
}
