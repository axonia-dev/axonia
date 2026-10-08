import { findingsFor } from "@/features/health/domain/finding-routing";
import type { HealthSnapshot } from "@/features/health/domain/health";
import { Chapter } from "@/features/health/ui/Chapter";
import { FindingList } from "@/features/health/ui/FindingList";
import { ReuseFunnel } from "@/features/health/ui/ReuseFunnel";

export function PayoffChapter({
  snapshot,
}: Readonly<{ snapshot: HealthSnapshot }>) {
  return (
    <Chapter
      id="payoff"
      title="Is it paying off"
      question="did anything the community produced get used?"
    >
      <article className="border-border bg-surface rounded-md border p-5">
        <ReuseFunnel
          reuse={snapshot.reuse}
          openings={snapshot.series.openings}
          saves={snapshot.series.saves}
        />
      </article>
      <FindingList
        findings={findingsFor("payoff", snapshot.diagnosis.findings)}
        snapshot={snapshot}
      />
    </Chapter>
  );
}
