import type { LucideIcon } from "lucide-react";
import { Lightbulb, Smile } from "lucide-react";
import type { ReactNode } from "react";

import { AverageBars } from "@/components/patterns/average-bars/AverageBars";
import { formatAverage } from "@/components/patterns/average-bars/format-average";
import type { HealthSnapshot } from "@/features/health/domain/health";
import { plural } from "@/lib/plural";

type Learning = HealthSnapshot["learning"];

export function BlockLabel({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <h3 className="text-muted-foreground mt-4 text-xs font-semibold">
      {children}
    </h3>
  );
}

/** Compared at the one decimal shown, so 8.26 against 8.3 never reads as a fall. */
function changeText(current: number, previous: number | null): string | null {
  if (previous === null) return null;
  const now = Math.round(current * 10);
  const before = Math.round(previous * 10);
  if (now > before) return `up from ${formatAverage(previous)}`;
  if (now < before) return `down from ${formatAverage(previous)}`;
  return "same as before";
}

function AverageRow({
  icon: Icon,
  label,
  average,
  previous,
}: Readonly<{
  icon: LucideIcon;
  label: string;
  average: number | null;
  previous: number | null;
}>) {
  if (average === null) return null;
  const change = changeText(average, previous);
  return (
    <li className="col-span-3 grid grid-cols-subgrid items-center">
      <span className="text-foreground flex items-center gap-[7px] text-[13px] font-semibold">
        <Icon aria-hidden className="text-data-1 size-4 shrink-0" />
        {label}
      </span>
      <AverageBars value={average} label={label} />
      <span className="text-muted-foreground text-xs whitespace-nowrap">
        <span
          aria-hidden="true"
          className="font-display text-foreground text-[15px] font-semibold"
        >
          {formatAverage(average)}
        </span>
        {change !== null && ` ${change}`}
      </span>
    </li>
  );
}

function EmptyRoom({ sessionsTooFew }: Readonly<{ sessionsTooFew: number }>) {
  return (
    <p className="text-muted-foreground mt-3 text-[13px]">
      {sessionsTooFew > 0 ? "Too few answers to show" : "No feedback yet"}
    </p>
  );
}

/** What attendees said of the sessions held in the window, counted only once each was revealed. */
export function LearningBlock({ learning }: Readonly<{ learning: Learning }>) {
  return (
    <div className="min-w-0">
      <BlockLabel>In the room</BlockLabel>
      {learning.sessions === 0 ? (
        <EmptyRoom sessionsTooFew={learning.sessionsTooFew} />
      ) : (
        <>
          <ul className="mt-3 grid grid-cols-[5.5rem_minmax(0,18rem)_auto] justify-start gap-3">
            <AverageRow
              icon={Lightbulb}
              label="Learned"
              average={learning.learned}
              previous={learning.previousLearned}
            />
            <AverageRow
              icon={Smile}
              label="Enjoyed"
              average={learning.enjoyed}
              previous={learning.previousEnjoyed}
            />
          </ul>
          <p className="text-muted-foreground mt-2.5 text-xs">
            {`${plural(learning.answers, "answer")} across ${plural(learning.sessions, "session")}`}
          </p>
        </>
      )}
    </div>
  );
}
