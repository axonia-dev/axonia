import type { HealthSnapshot } from "@/features/health/domain/health";
import { BlockLabel, LearningBlock } from "@/features/health/ui/LearningBlock";
import { Panel } from "@/features/health/ui/Panel";
import { ReuseFunnel } from "@/features/health/ui/ReuseFunnel";

/** Paying off means knowledge was reused or people learned, so both readings sit side by side. */
export function PayoffPanel({
  snapshot,
}: Readonly<{ snapshot: HealthSnapshot }>) {
  return (
    <Panel
      id="payoff"
      title="Is it paying off"
      status={snapshot.payoff}
      snapshot={snapshot}
    >
      <div className="grid grid-cols-1 gap-x-7 xl:grid-cols-2">
        <div className="min-w-0">
          <BlockLabel>Library</BlockLabel>
          <ReuseFunnel reuse={snapshot.reuse} />
        </div>
        <LearningBlock learning={snapshot.learning} />
      </div>
    </Panel>
  );
}
