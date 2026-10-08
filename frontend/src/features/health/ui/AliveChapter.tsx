import { findingsFor } from "@/features/health/domain/finding-routing";
import type { HealthSnapshot } from "@/features/health/domain/health";
import {
  CADENCE_WORD,
  cadenceEvidence,
  formatShare,
} from "@/features/health/domain/health";
import { participationRead } from "@/features/health/domain/participation";
import { CadencePulse } from "@/features/health/ui/CadencePulse";
import { Chapter } from "@/features/health/ui/Chapter";
import { DialCard } from "@/features/health/ui/DialCard";
import { FindingList } from "@/features/health/ui/FindingList";
import { Sparkline } from "@/features/health/ui/Sparkline";
import { TrendChip } from "@/features/health/ui/TrendChip";
import { plural } from "@/lib/plural";

const FALLBACK_WINDOW_DAYS = 90;

/**
 * Read from the snapshot rather than from the window control, so the pulse and the numbers beside it
 * always describe the same window, including while a refetch is still in flight.
 */
function windowDaysOf(window: string): number {
  const days = Number.parseInt(window, 10);
  return Number.isNaN(days) ? FALLBACK_WINDOW_DAYS : days;
}

/**
 * The status word plus evidence, and evidence only. The reading of that evidence is a sentence, and
 * every sentence on this page belongs to exactly one finding.
 */
function CadenceCard({ snapshot }: Readonly<{ snapshot: HealthSnapshot }>) {
  const { cadence } = snapshot;
  const evidence = cadenceEvidence(cadence);
  return (
    <article className="border-border bg-surface rounded-md border p-5">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <div className="flex items-baseline gap-3">
          <span className="font-display text-foreground text-4xl leading-none font-bold">
            {cadence.sessionsHeld}
          </span>
          <span className="text-foreground text-base font-semibold">
            {cadence.sessionsHeld === 1 ? "session held" : "sessions held"}
          </span>
          <TrendChip direction={cadence.trend.direction}>
            from {cadence.trend.previous}
          </TrendChip>
        </div>
        <span className="flex flex-col items-end max-sm:items-start">
          <span className="text-foreground text-base font-semibold">
            {CADENCE_WORD[cadence.status]}
          </span>
          {evidence && (
            <span className="text-muted-foreground text-sm">{evidence}</span>
          )}
        </span>
      </div>
      <div className="mt-3">
        <CadencePulse
          heldSessionDates={snapshot.heldSessionDates}
          windowDays={windowDaysOf(snapshot.window)}
          generatedAt={snapshot.generatedAt}
        />
      </div>
    </article>
  );
}

function Dials({ snapshot }: Readonly<{ snapshot: HealthSnapshot }>) {
  const { participation, engagement, membershipGrowth } = snapshot;
  const breadth = participationRead(participation);
  return (
    <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
      <DialCard
        title="Participation breadth"
        value={breadth.value}
        chip={
          breadth.showTrend ? (
            <TrendChip direction={participation.trend.direction}>
              from {formatShare(participation.trend.previous)}
            </TrendChip>
          ) : undefined
        }
        read="Contributed or engaged in the window."
        sparkline={
          <Sparkline
            values={snapshot.series.participation}
            label="members engaged per bucket"
          />
        }
      />
      <DialCard
        title="Engagement"
        value={plural(engagement.loves, "love")}
        chip={
          <TrendChip direction={engagement.trend.direction}>
            from {engagement.trend.previous}
          </TrendChip>
        }
        read="Given on contributions, aggregate only."
        sparkline={
          <Sparkline
            values={snapshot.series.engagement}
            label="loves per bucket"
          />
        }
      />
      {/* The chip compares joins, so it sits beside the joins figure: beside the member count it
          would read as 80 members up from 2. */}
      <DialCard
        title="Membership"
        value={plural(membershipGrowth.memberCount, "member")}
        read={
          <>
            {membershipGrowth.joins} joined this window{" "}
            <TrendChip direction={membershipGrowth.trend.direction}>
              from {membershipGrowth.trend.previous}
            </TrendChip>
          </>
        }
        sparkline={
          <Sparkline values={snapshot.series.joins} label="joins per bucket" />
        }
      />
    </div>
  );
}

export function AliveChapter({
  snapshot,
}: Readonly<{ snapshot: HealthSnapshot }>) {
  return (
    <Chapter
      id="alive"
      title="Is it alive"
      question="is anything actually happening?"
    >
      <CadenceCard snapshot={snapshot} />
      <FindingList
        findings={findingsFor("alive", snapshot.diagnosis.findings)}
        snapshot={snapshot}
      />
      <Dials snapshot={snapshot} />
    </Chapter>
  );
}
