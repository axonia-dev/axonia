import type { HealthSnapshot } from "@/features/health/domain/health";
import { Panel } from "@/features/health/ui/Panel";
import { ReuseFunnel } from "@/features/health/ui/ReuseFunnel";

export function PayoffPanel({
  snapshot,
}: Readonly<{ snapshot: HealthSnapshot }>) {
  return (
    <Panel
      id="payoff"
      title="Is it paying off"
      status={snapshot.reuse.status}
      snapshot={snapshot}
    >
      <ReuseFunnel reuse={snapshot.reuse} />
    </Panel>
  );
}
