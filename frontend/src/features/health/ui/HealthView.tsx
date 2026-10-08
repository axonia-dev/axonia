import { useState } from "react";

import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { useHealthSnapshot } from "@/features/health/api/use-health";
import type { HealthSnapshot } from "@/features/health/domain/health";
import { verdictsOf } from "@/features/health/domain/verdict";
import { AliveChapter } from "@/features/health/ui/AliveChapter";
import { CarriedChapter } from "@/features/health/ui/CarriedChapter";
import { PayoffChapter } from "@/features/health/ui/PayoffChapter";
import { QuietCommunityCard } from "@/features/health/ui/QuietCommunityCard";
import { VerdictStrip } from "@/features/health/ui/VerdictStrip";
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
 * Three chapters, each holding the evidence for its own findings. No chapter restates another and
 * no panel summarises them all: a finding can only be stated where its evidence is drawn.
 */
function Chapters({
  snapshot,
  slug,
}: Readonly<{ snapshot: HealthSnapshot; slug: string }>) {
  return (
    <>
      <VerdictStrip verdicts={verdictsOf(snapshot)} />
      <CarriedChapter snapshot={snapshot} />
      <AliveChapter snapshot={snapshot} />
      <PayoffChapter snapshot={snapshot} />
      {snapshot.cadence.status === "DORMANT" && (
        <div className="mt-9">
          <QuietCommunityCard
            slug={slug}
            joins={snapshot.membershipGrowth.joins}
          />
        </div>
      )}
    </>
  );
}

export function HealthView({ slug }: Readonly<{ slug: string }>) {
  const [windowId, setWindowId] = useState("90d");
  const snapshot = useHealthSnapshot(slug, windowId);
  // The window being described is the one the data came from, never the control's newer selection:
  // during a refetch those disagree, and the header would date a snapshot it is not showing.
  const shownWindow = snapshot.data?.window ?? windowId;

  return (
    <section className="flex flex-col">
      <header className="flex items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-foreground text-2xl font-bold">
            Community health
          </h1>
          <p className="text-muted-foreground text-sm">
            vs. the previous {shownWindow.replace("d", " days")}
          </p>
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
      {snapshot.data && (
        <div className="mt-6">
          <Chapters snapshot={snapshot.data} slug={slug} />
        </div>
      )}
    </section>
  );
}
