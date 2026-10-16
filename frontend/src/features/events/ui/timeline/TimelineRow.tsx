import type { MouseEvent as ReactMouseEvent } from "react";

import { countdownPhase } from "@/features/events/domain/next-countdown";
import { entryDayIso } from "@/features/events/domain/occurrence-day";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { JoinControl } from "@/features/events/ui/JoinControl";
import { stillToHappen } from "@/features/events/ui/SessionPlace";
import type { EntryDateParts } from "@/features/events/ui/timeline/timeline";
import { dateParts } from "@/features/events/ui/timeline/timeline";
import { TimelineCard } from "@/features/events/ui/timeline/TimelineCard";
import type { User } from "@/features/identity/domain/user";
import { cn } from "@/lib/cn";
import { useNow } from "@/lib/use-wake-at";

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

function dayInkClass(ghost: boolean, memory: boolean): string {
  if (ghost) return "text-muted-foreground";
  return memory ? "text-ink-sepia" : "text-foreground";
}

function weekdayInkClass(ghost: boolean, memory: boolean): string {
  if (ghost) return "text-muted-foreground";
  return memory ? "text-ink-sepia" : "text-primary";
}

function Caption({
  parts,
  caption,
  memory,
}: Readonly<{
  parts: EntryDateParts;
  caption: string | null | undefined;
  memory: boolean;
}>) {
  if (caption === null || (caption === undefined && parts.relative === null)) {
    return null;
  }
  return (
    <div
      className={cn(
        "mt-0.5 text-[11px]",
        memory ? "text-ink-sepia" : "text-muted-foreground",
      )}
    >
      {caption ?? (
        <>
          <span className="hidden sm:inline">{parts.relative}</span>
          <span className="sm:hidden">{parts.relativeShort}</span>
        </>
      )}
    </div>
  );
}

export function DateCell({
  parts,
  ghost,
  caption,
  tone = "default",
}: Readonly<{
  parts: EntryDateParts;
  ghost: boolean;
  caption?: string | null;
  tone?: "default" | "memory";
}>) {
  const memory = tone === "memory";
  return (
    <>
      <div className={cn("font-display font-bold", dayInkClass(ghost, memory))}>
        <span
          className={cn(
            "block text-[9px] font-bold tracking-widest uppercase sm:hidden",
            weekdayInkClass(ghost, memory),
          )}
        >
          {parts.weekdayShort}
        </span>
        <span className="text-base leading-tight">
          <span className="hidden sm:inline">{parts.weekdayShort} </span>
          {parts.day}
        </span>
      </div>
      <Caption parts={parts} caption={caption} memory={memory} />
    </>
  );
}

function dotClass(
  ghost: boolean,
  nextUp: boolean,
  variant: "upcoming" | "memory",
): string {
  if (variant === "memory") return "bg-paper border-ink-sepia-soft border-2";
  if (ghost) {
    return "border-muted-foreground group-hover:border-primary border-2 border-dashed bg-transparent group-hover:border-solid";
  }
  return nextUp
    ? "bg-primary group-hover:ring-primary/20 group-hover:ring-4"
    : "bg-surface border-primary group-hover:ring-primary/20 border-2 group-hover:ring-4";
}

export function RailDot({
  ghost,
  nextUp,
  variant = "upcoming",
}: Readonly<{
  ghost: boolean;
  nextUp: boolean;
  variant?: "upcoming" | "memory";
}>) {
  return (
    <span
      className={cn(
        "absolute top-[1.15rem] left-1 size-2.5 rounded-full transition-all duration-200",
        "motion-safe:group-hover:scale-110",
        dotClass(ghost, nextUp, variant),
      )}
    />
  );
}

function NextJoin({ entry }: Readonly<{ entry: ScheduleEntry }>) {
  const now = useNow();
  const kind = countdownPhase(entry, now)?.kind;
  const { joinUrl } = entry.place;
  if (entry.scheduledAt === null || joinUrl === null) return null;
  if (kind !== "last-hour" && kind !== "running") return null;
  return (
    <JoinControl
      joinUrl={joinUrl}
      scheduledAt={entry.scheduledAt}
      hasTime={entry.hasTime}
      stillToHappen={stillToHappen(entry.status)}
      durationMinutes={entry.durationMinutes}
      statesShut
      phaseStatedElsewhere
      className="mt-2"
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
      <div className="min-w-0">
        <TimelineCard
          entry={entry}
          label={label}
          href={href}
          nextUp={nextUp}
          users={users}
        />
        {nextUp && <NextJoin entry={entry} />}
      </div>
    </li>
  );
}
