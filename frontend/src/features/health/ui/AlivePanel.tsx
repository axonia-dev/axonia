import type { HealthSnapshot } from "@/features/health/domain/health";
import {
  dayMonth,
  wholeDaysBetween,
  windowDaysOf,
} from "@/features/health/domain/window";
import { Fact, Facts, Panel, SubLabel } from "@/features/health/ui/Panel";
import { SessionTimeline } from "@/features/health/ui/SessionTimeline";
import { TrendNote } from "@/features/health/ui/TrendNote";
import { plural, pluralWord } from "@/lib/plural";

/**
 * The median gap when the window has one; otherwise how long the community has been quiet, read
 * from the snapshot's own instant so the page never reads the clock. A community that never held
 * a session has no such figure.
 */
function GapFact({ snapshot }: Readonly<{ snapshot: HealthSnapshot }>) {
  const { medianGapDays, lastHeldAt } = snapshot.cadence;
  if (medianGapDays !== null) {
    return (
      <Fact
        value={
          medianGapDays === 0 ? "Under a day" : plural(medianGapDays, "day")
        }
        caption="typical gap between sessions"
      />
    );
  }
  if (lastHeldAt === null) return null;
  return (
    <Fact
      value={plural(wholeDaysBetween(lastHeldAt, snapshot.generatedAt), "day")}
      caption="since the last session"
    />
  );
}

function NextFact({
  nextSessionAt,
}: Readonly<{ nextSessionAt: string | null }>) {
  if (nextSessionAt === null) {
    return <Fact value="None" caption="scheduled" muted />;
  }
  return (
    <Fact value={dayMonth(Date.parse(nextSessionAt))} caption="next session" />
  );
}

export function AlivePanel({
  snapshot,
}: Readonly<{ snapshot: HealthSnapshot }>) {
  const { cadence, membershipGrowth, engagement } = snapshot;
  return (
    <Panel
      id="alive"
      title="Is it alive"
      status={cadence.status}
      snapshot={snapshot}
    >
      <Facts>
        <GapFact snapshot={snapshot} />
        <NextFact nextSessionAt={cadence.nextSessionAt} />
      </Facts>
      <SubLabel
        start="Sessions"
        end={`${windowDaysOf(snapshot.window)} days`}
      />
      <SessionTimeline
        heldSessionDates={snapshot.heldSessionDates}
        nextSessionAt={cadence.nextSessionAt}
        windowId={snapshot.window}
        generatedAt={snapshot.generatedAt}
      />
      <Facts>
        <Fact
          small
          value={membershipGrowth.joins}
          trend={
            <TrendNote
              current={membershipGrowth.joins}
              trend={membershipGrowth.trend}
            />
          }
          caption="joined"
        />
        <Fact
          small
          value={engagement.loves}
          trend={
            <TrendNote current={engagement.loves} trend={engagement.trend} />
          }
          caption={`${pluralWord(engagement.loves, "love")} given`}
        />
      </Facts>
    </Panel>
  );
}
