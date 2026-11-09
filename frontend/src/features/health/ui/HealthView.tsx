import { useState } from "react";

import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { useHealthSnapshot } from "@/features/health/api/use-health";
import type { HealthSnapshot } from "@/features/health/domain/health";
import { summaryOf } from "@/features/health/domain/summary";
import { windowDaysOf } from "@/features/health/domain/window";
import { ActivityChart } from "@/features/health/ui/ActivityChart";
import { AlivePanel } from "@/features/health/ui/AlivePanel";
import { CarriedPanel } from "@/features/health/ui/CarriedPanel";
import { GlanceRow } from "@/features/health/ui/GlanceRow";
import { PayoffPanel } from "@/features/health/ui/PayoffPanel";
import { QuietCommunityCard } from "@/features/health/ui/QuietCommunityCard";
import { ApiError } from "@/lib/api-client";

const WINDOWS = [
  { id: "30d", label: "30 days" },
  { id: "90d", label: "90 days" },
  { id: "180d", label: "180 days" },
];

function errorMessage(error: unknown): string {
  if (error instanceof ApiError && error.status === 403) {
    return "Only this community's organizers can see its health.";
  }
  return "Could not load the health snapshot.";
}

/**
 * A glance row, one chart, then a panel per question with its findings beneath its evidence. No
 * panel restates another and nothing summarises the findings: a finding is stated where its
 * evidence is drawn, and only there.
 */
function Dashboard({
  snapshot,
  slug,
}: Readonly<{ snapshot: HealthSnapshot; slug: string }>) {
  return (
    <div className="mt-[22px] flex flex-col gap-3.5">
      <GlanceRow snapshot={snapshot} />
      <ActivityChart
        series={snapshot.series}
        windowId={snapshot.window}
        generatedAt={snapshot.generatedAt}
      />
      <div className="grid grid-cols-1 gap-3.5 lg:grid-cols-2">
        <CarriedPanel snapshot={snapshot} />
        <AlivePanel snapshot={snapshot} />
      </div>
      <PayoffPanel snapshot={snapshot} />
      {snapshot.cadence.rhythm === "DORMANT" && (
        <QuietCommunityCard
          slug={slug}
          joins={snapshot.membershipGrowth.joins}
        />
      )}
    </div>
  );
}

export function HealthView({ slug }: Readonly<{ slug: string }>) {
  const [windowId, setWindowId] = useState("90d");
  const snapshot = useHealthSnapshot(slug, windowId);
  // The window being described is the one the data came from, never the control's newer selection:
  // during a refetch those disagree, and the header would date a snapshot it is not showing.
  const days = windowDaysOf(snapshot.data?.window ?? windowId);

  return (
    <section className="flex flex-col">
      <header className="flex flex-wrap items-start justify-between gap-x-6 gap-y-4">
        <div className="flex max-w-[640px] min-w-0 flex-col">
          <h1 className="font-display text-foreground text-2xl font-semibold">
            Community health
          </h1>
          <p className="text-muted-foreground mt-1 text-[13px]">
            Last {days} days, compared with the {days} days before
          </p>
          {snapshot.data && (
            <p className="text-foreground mt-2.5 text-[15px] leading-normal">
              {summaryOf(snapshot.data).map(({ text, emphasised }, index) =>
                emphasised ? (
                  <strong key={`${index}-${text}`} className="font-semibold">
                    {text}
                  </strong>
                ) : (
                  text
                ),
              )}
            </p>
          )}
        </div>
        <SegmentedControl
          label="Window"
          hideLabel
          value={windowId}
          options={WINDOWS}
          onChange={setWindowId}
        />
      </header>
      {snapshot.isLoading && (
        <p className="text-muted-foreground mt-6 text-sm">Loading…</p>
      )}
      {snapshot.isError && (
        <p role="alert" className="text-danger mt-6 text-sm">
          {errorMessage(snapshot.error)}
        </p>
      )}
      {snapshot.data && <Dashboard snapshot={snapshot.data} slug={slug} />}
    </section>
  );
}
