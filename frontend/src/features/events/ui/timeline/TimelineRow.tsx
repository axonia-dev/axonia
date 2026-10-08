import type { MouseEvent as ReactMouseEvent } from "react";

import { entryDayIso } from "@/features/events/domain/occurrence-day";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import type { EntryDateParts } from "@/features/events/ui/timeline/timeline";
import { dateParts } from "@/features/events/ui/timeline/timeline";
import { TimelineCard } from "@/features/events/ui/timeline/TimelineCard";
import type { User } from "@/features/identity/domain/user";
import { cn } from "@/lib/cn";

/** Shared by every timeline row shape so rail offsets stay aligned (and jscpd quiet). */
export const rowGridClass =
  "grid grid-cols-[2.75rem_1.375rem_minmax(0,1fr)] items-start sm:grid-cols-[5.25rem_2.25rem_minmax(0,1fr)]";

interface Props {
  entry: ScheduleEntry;
  nextUp: boolean;
  label: string;
  href: string;
  todayIso: string;
  users: Record<string, User>;
  /** A right-click on this row, when its owner has something to offer for it. Absent for a
   *  reader with no commands, whose browser menu is then left alone. */
  onContextMenu?: (event: ReactMouseEvent) => void;
}

function DateCell({
  parts,
  ghost,
}: Readonly<{ parts: EntryDateParts; ghost: boolean }>) {
  return (
    <>
      <div
        className={cn(
          "font-display font-bold",
          ghost ? "text-muted-foreground" : "text-foreground",
        )}
      >
        <span
          className={cn(
            "block text-[9px] font-bold tracking-widest uppercase sm:hidden",
            ghost ? "text-muted-foreground" : "text-primary",
          )}
        >
          {parts.weekdayShort}
        </span>
        <span className="text-base leading-tight">
          <span className="hidden sm:inline">{parts.weekdayShort} </span>
          {parts.day}
        </span>
      </div>
      {parts.relative !== null && (
        <div className="text-muted-foreground mt-0.5 text-[11px]">
          <span className="hidden sm:inline">{parts.relative}</span>
          <span className="sm:hidden">{parts.relativeShort}</span>
        </div>
      )}
    </>
  );
}

function RailDot({
  ghost,
  nextUp,
}: Readonly<{ ghost: boolean; nextUp: boolean }>) {
  return (
    <span
      className={cn(
        "absolute top-[1.15rem] left-1 size-2.5 rounded-full transition-all duration-200",
        "motion-safe:group-hover:scale-110",
        ghost &&
          "border-muted-foreground group-hover:border-primary border-2 border-dashed bg-transparent group-hover:border-solid",
        !ghost &&
          nextUp &&
          "bg-primary group-hover:ring-primary/20 group-hover:ring-4",
        !ghost &&
          !nextUp &&
          "bg-surface border-primary group-hover:ring-primary/20 border-2 group-hover:ring-4",
      )}
    />
  );
}

export function TimelineRow({
  entry,
  nextUp,
  label,
  href,
  todayIso,
  users,
  onContextMenu,
}: Readonly<Props>) {
  const iso = entryDayIso(entry);
  return (
    <li
      className={cn(
        // Lift the hovered/focused row above later sibling rows so the presenter
        // hover card is not overpainted by the next entry (ghost cards use
        // opacity < 1, which makes them a stacking context at the same z-level).
        "timeline-row group relative pb-3 focus-within:z-10 hover:z-10",
        rowGridClass,
      )}
      {...(onContextMenu === undefined ? {} : { onContextMenu })}
    >
      <div className="pt-2.5 pr-1.5 text-right">
        {iso !== "" && (
          <DateCell parts={dateParts(iso, todayIso)} ghost={entry.virtual} />
        )}
      </div>
      <div className="relative h-full" aria-hidden="true">
        <RailDot ghost={entry.virtual} nextUp={nextUp} />
      </div>
      <TimelineCard
        entry={entry}
        label={label}
        href={href}
        nextUp={nextUp}
        users={users}
      />
    </li>
  );
}
