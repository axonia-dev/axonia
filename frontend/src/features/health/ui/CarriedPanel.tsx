import type { HealthSnapshot } from "@/features/health/domain/health";
import { Fact, Facts, Panel, SubLabel } from "@/features/health/ui/Panel";
import { StageBar } from "@/features/health/ui/StageBar";
import { TrendNote } from "@/features/health/ui/TrendNote";
import { plural, pluralWord } from "@/lib/plural";

type Stage = HealthSnapshot["stage"];
type Reach = HealthSnapshot["reach"];

function StageFact({ stage }: Readonly<{ stage: Stage }>) {
  if (stage.talks === 0) return <Fact value={0} caption="talks given" />;
  if (stage.speakers === 1) {
    return <Fact value={1} caption="speaker on stage" />;
  }
  const { speakersForHalf } = stage;
  return (
    <Fact
      value={speakersForHalf}
      caption={
        speakersForHalf === 1
          ? "speaker gives half the talks"
          : "speakers give half the talks"
      }
    />
  );
}

function ReachFact({ reach }: Readonly<{ reach: Reach }>) {
  if (!reach.available) return null;
  return (
    <Fact
      value={reach.teams}
      trend={<TrendNote current={reach.teams} trend={reach.trend} />}
      caption={`${pluralWord(reach.teams, "team")} on stage`}
    />
  );
}

function unitsOf({ departments, businessUnits }: Reach): string | null {
  const parts = [
    departments > 0 ? plural(departments, "department") : null,
    businessUnits > 0 ? plural(businessUnits, "business unit") : null,
  ].filter((part) => part !== null);
  return parts.length === 0 ? null : parts.join(" and ");
}

/**
 * Counts only, never a unit's name, and its own coverage stated out loud when partial. Reach is
 * unavailable whenever no speaker has team data, which is every window without a talk, so the
 * line is only worth saying when there were speakers.
 */
function ReachDetail({
  reach,
  speakers,
}: Readonly<{ reach: Reach; speakers: number }>) {
  if (!reach.available) {
    if (speakers === 0) return null;
    return (
      <p className="text-muted-foreground mt-2 text-xs">
        Teams on stage unavailable: no speaker has team data.
      </p>
    );
  }
  const units = unitsOf(reach);
  const partial = reach.speakersWithUnit < reach.totalSpeakers;
  if (units === null && !partial) return null;
  return (
    <div className="text-muted-foreground mt-2 flex flex-wrap gap-x-4 text-xs">
      {units && <p>{units}</p>}
      {partial && (
        <p>
          {reach.speakersWithUnit} of {reach.totalSpeakers} speakers with team
          data
        </p>
      )}
    </div>
  );
}

export function CarriedPanel({
  snapshot,
}: Readonly<{ snapshot: HealthSnapshot }>) {
  const { stage, curation, reach } = snapshot;
  return (
    <Panel
      id="carried"
      title="Who carries it"
      status={stage.status}
      snapshot={snapshot}
    >
      <Facts>
        <StageFact stage={stage} />
        <ReachFact reach={reach} />
      </Facts>
      <ReachDetail reach={reach} speakers={stage.speakers} />
      {stage.distribution.length > 0 && (
        <>
          <SubLabel start="On stage" end={plural(stage.speakers, "speaker")} />
          <StageBar
            name="stage"
            shares={stage.distribution}
            strong={stage.speakersForHalf}
            tone="data-1"
          />
        </>
      )}
      {curation.acts > 0 && (
        <>
          <SubLabel start="Behind the scenes" end="topics and Library" />
          <StageBar
            name="curation"
            shares={[curation.topShare, 1 - curation.topShare].filter(
              (share) => share > 0,
            )}
            strong={1}
            tone="data-2"
            thin
          />
        </>
      )}
    </Panel>
  );
}
