import { findingsFor } from "@/features/health/domain/finding-routing";
import type { HealthSnapshot } from "@/features/health/domain/health";
import { Chapter } from "@/features/health/ui/Chapter";
import { ContributionSpreadCard } from "@/features/health/ui/ContributionSpreadCard";
import { CrossTeamReachCard } from "@/features/health/ui/CrossTeamReachCard";
import { FindingList } from "@/features/health/ui/FindingList";

/** Semantic, never the community accent: fragility is not a place for a brand colour. */
function fragilityTone(busFactor: number): string {
  if (busFactor === 0) return "var(--danger)";
  if (busFactor <= 2) return "var(--warning)";
  return "var(--success)";
}

export function CarriedChapter({
  snapshot,
}: Readonly<{ snapshot: HealthSnapshot }>) {
  return (
    <Chapter
      id="carried"
      title="Who carries it"
      question="how fragile is this?"
    >
      <ContributionSpreadCard
        busFactor={snapshot.busFactor}
        distribution={snapshot.concentration.distribution}
        contributors={snapshot.series.contributors}
        tone={fragilityTone(snapshot.busFactor)}
      />
      <FindingList
        findings={findingsFor("carried", snapshot.diagnosis.findings)}
        snapshot={snapshot}
      />
      <div className="mt-3">
        <CrossTeamReachCard reach={snapshot.reach} />
      </div>
    </Chapter>
  );
}
