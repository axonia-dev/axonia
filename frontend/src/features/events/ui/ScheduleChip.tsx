import {
  joinPhase,
  nextPhaseChange,
} from "@/features/events/domain/join-window";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { resolveLabel, timeLabel } from "@/features/events/domain/schedule";
import type { Series } from "@/features/events/domain/series";
import { cn } from "@/lib/cn";
import { useNow, useWakeAt } from "@/lib/use-wake-at";

/** A real chip links to its session; a live ghost to its preview; else inert. */
export function chipHref(entry: ScheduleEntry, slug: string): string | null {
  if (entry.sessionId !== null) {
    return `/communities/${slug}/sessions/${entry.sessionId}`;
  }
  if (
    entry.virtual &&
    entry.status === "SCHEDULED" &&
    entry.seriesId !== null &&
    entry.occurrenceDate !== null
  ) {
    return `/communities/${slug}/series/${entry.seriesId}/occurrences/${entry.occurrenceDate}`;
  }
  return null;
}

function chipClass(entry: ScheduleEntry): string {
  if (entry.virtual)
    return "border-l-2 border-dashed border-primary bg-primary/5 text-muted-foreground";
  if (entry.status === "HELD")
    return "border-l-2 border-border-strong bg-surface-sunken text-muted-foreground";
  if (entry.status === "CANCELLED") return "text-muted-foreground opacity-50";
  if (entry.status === "SKIPPED")
    return "text-muted-foreground line-through opacity-60";
  return entry.seriesId === null
    ? "border-l-2 border-secondary bg-secondary/15 text-secondary"
    : "border-l-2 border-primary bg-primary/15 text-primary";
}

function ChipBody({
  entry,
  label,
  live,
  nextUp,
}: Readonly<{
  entry: ScheduleEntry;
  label: string;
  live: boolean;
  nextUp: boolean;
}>) {
  const time = timeLabel(entry);
  return (
    <>
      {live && (
        <span
          data-testid="chip-live"
          aria-label="Live now"
          className="size-1.5 shrink-0 rounded-full bg-current motion-safe:animate-[signal-pulse_2.2s_ease-out_infinite]"
        />
      )}
      {!live && time !== null && (
        <span
          data-testid="chip-time"
          className="shrink-0 font-semibold tabular-nums"
        >
          {time}
        </span>
      )}
      <span className="truncate">{label}</span>
      {nextUp && (
        <span data-testid="chip-next-up" className="sr-only">
          Up next
        </span>
      )}
    </>
  );
}

/** What a chip that opens a panel says about it: which panel, and whether it is showing. Omitted
 *  entirely for a chip that opens none, so no chip claims to control something that is not there. */
function disclosureProps(
  panelId: string | undefined,
  selected: boolean,
): Record<string, string | boolean> {
  if (panelId === undefined) return {};
  return { "aria-expanded": selected, "aria-controls": panelId };
}

export interface ChipProps {
  entry: ScheduleEntry;
  seriesList: Series[];
  selected?: boolean;
  nextUp?: boolean;
  /** The id of the panel this chip opens, so the button states what it controls and whether it is
   *  currently showing. Absent for a chip that opens no panel. */
  panelId?: string;
  /** A single click or Enter. Absent for a chip nothing can be done with. */
  onSelect?: () => void;
  /** A double click, which goes through to the session itself. */
  onOpen?: () => void;
}

/** Whether the session is running right now, and the instant that stops being true. A chip with
 *  no start, no chosen hour or no way in has no phase worth following and registers no wake-up. */
function useLivePhase(entry: ScheduleEntry): boolean {
  const followable =
    entry.scheduledAt !== null && entry.hasTime && entry.status === "SCHEDULED";
  const judged = {
    scheduledAt: entry.scheduledAt ?? "",
    durationMinutes: entry.durationMinutes,
    now: useNow(),
  };
  const live = followable && joinPhase(judged) === "live";
  useWakeAt(followable ? nextPhaseChange(judged) : null);
  return live;
}

export function Chip({
  entry,
  seriesList,
  selected = false,
  nextUp = false,
  panelId,
  onSelect,
  onOpen,
}: Readonly<ChipProps>) {
  const label = resolveLabel(entry, seriesList);
  const live = useLivePhase(entry);

  const className = cn(
    "flex w-full items-center gap-1.5 overflow-hidden rounded px-1 py-0.5 text-left text-xs leading-tight",
    chipClass(entry),
    nextUp && "ring-primary/60 ring-1",
    selected && "ring-primary ring-2",
    (onSelect ?? onOpen) && "cursor-pointer hover:opacity-80",
  );

  const body = (
    <ChipBody entry={entry} label={label} live={live} nextUp={nextUp} />
  );

  if (onSelect === undefined && onOpen === undefined) {
    return (
      <span className={className} title={label}>
        {body}
      </span>
    );
  }

  return (
    <button
      type="button"
      data-chip
      aria-current={selected}
      {...disclosureProps(panelId, selected)}
      title={label}
      className={className}
      onClick={onSelect ?? onOpen}
      onDoubleClick={onOpen}
    >
      {body}
    </button>
  );
}
