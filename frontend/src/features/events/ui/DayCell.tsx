import { useDraggable, useDroppable } from "@dnd-kit/core";

import type { GridCell } from "@/features/events/domain/month-grid";
import type { ScheduleEntry } from "@/features/events/domain/schedule";
import { resolveLabel } from "@/features/events/domain/schedule";
import type { Series } from "@/features/events/domain/series";
import { dragId, isDraggableEntry } from "@/features/events/ui/CalendarDnd";
import { Chip, type ChipProps } from "@/features/events/ui/ScheduleChip";
import type { ContextTarget } from "@/features/events/ui/ScheduleContextMenu";
import { cn } from "@/lib/cn";

type OnContextMenu = (target: ContextTarget, x: number, y: number) => void;

function dotClass(entry: ScheduleEntry): string {
  if (entry.virtual) return "border border-dashed border-primary";
  if (entry.status === "HELD") return "bg-muted-foreground";
  if (entry.status === "CANCELLED" || entry.status === "SKIPPED")
    return "bg-muted-foreground opacity-50";
  return entry.seriesId === null ? "bg-secondary" : "bg-primary";
}

function dayNumTone(isToday: boolean, inMonth: boolean): string {
  if (isToday) return "bg-primary text-primary-foreground font-semibold";
  return inMonth ? "text-foreground" : "text-muted-foreground";
}

function cellTone(isToday: boolean, inMonth: boolean): string {
  if (isToday) return "bg-primary/[0.07]";
  return inMonth ? "bg-surface" : "bg-surface-sunken";
}

export function entryKey(
  entry: ScheduleEntry,
  iso: string,
  idx: number,
): string {
  return entry.sessionId !== null
    ? String(entry.sessionId)
    : `${entry.seriesId ?? "x"}-${iso}-${idx}`;
}

/** A chip an organizer can drag onto another day to reschedule the occurrence. */
function DraggableChip({
  entry,
  seriesList,
  selected,
  nextUp,
  panelId,
  onSelect,
  onOpen,
}: Readonly<ChipProps>) {
  const { setNodeRef, listeners, transform, isDragging } = useDraggable({
    id: dragId(entry),
    data: { entry },
  });
  const style = transform
    ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)` }
    : undefined;
  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn("cursor-grab touch-none", isDragging && "opacity-50")}
      {...listeners}
    >
      <Chip
        entry={entry}
        seriesList={seriesList}
        {...(selected !== undefined && { selected })}
        {...(nextUp !== undefined && { nextUp })}
        {...(panelId !== undefined && { panelId })}
        {...(onSelect && { onSelect })}
        {...(onOpen && { onOpen })}
      />
    </div>
  );
}

interface DayCellProps {
  cell: GridCell;
  entries: ScheduleEntry[];
  isToday: boolean;
  selected: boolean;
  highlighted?: boolean;
  dayLabel: string;
  onSelectDay: () => void;
  seriesList: Series[];
  canManage?: boolean;
  onContextMenu?: OnContextMenu;
  selectedKey?: string;
  /** The id of the panel this cell's chips open, passed straight through to them. */
  panelId?: string;
  onSelect?: (entry: ScheduleEntry) => void;
  onOpen?: (entry: ScheduleEntry) => void;
  nextUpEntry?: ScheduleEntry | null;
}

export function DayCell({
  cell,
  entries,
  isToday,
  selected,
  highlighted = false,
  dayLabel,
  onSelectDay,
  seriesList,
  canManage = false,
  onContextMenu,
  selectedKey,
  panelId,
  onSelect,
  onOpen,
  nextUpEntry = null,
}: Readonly<DayCellProps>) {
  const dayNum = Number(cell.isoDate.slice(8));
  const dayBase = cn(
    "flex h-5 w-5 items-center justify-center rounded-full text-xs",
    dayNumTone(isToday, cell.inMonth),
  );
  const { setNodeRef, isOver } = useDroppable({
    id: `day:${cell.isoDate}`,
    data: { iso: cell.isoDate },
  });

  function chipPropsFor(
    entry: ScheduleEntry,
    idx: number,
  ): Omit<ChipProps, "seriesList"> {
    return {
      entry,
      selected: selectedKey === entryKey(entry, cell.isoDate, idx),
      nextUp: entry === nextUpEntry,
      ...(panelId !== undefined && { panelId }),
      ...(onSelect && { onSelect: () => onSelect(entry) }),
      ...(onOpen && { onOpen: () => onOpen(entry) }),
    };
  }

  return (
    <div
      ref={setNodeRef}
      data-date={cell.isoDate}
      className={cn(
        // A cell takes an even share of whatever the window has left once the page around the
        // calendar is taken out, so a month fills the window and stops there rather than running
        // past the fold. Window height, not window width: a wide screen in a short window was
        // still overflowing when the two were read as the same thing.
        //
        // It is a minimum, not a height, so a day with more sessions than fit still grows and
        // takes its week's row with it rather than hiding them. Clamped at both ends: a short
        // window keeps a cell worth reading, a tall one fills instead of leaving a band of
        // nothing under the grid. 17rem is the page above and below the grid, and
        // --calendar-rows is this month's week count, published on the grid itself.
        "min-h-16 p-1 md:min-h-[clamp(6rem,calc((100dvh_-_17rem)/var(--calendar-rows,6)),12rem)]",
        cellTone(isToday, cell.inMonth),
        (isOver || highlighted) && "ring-primary ring-2 ring-inset",
        highlighted && "bg-primary/10",
      )}
      onContextMenu={
        onContextMenu
          ? (e) => {
              e.preventDefault();
              onContextMenu(
                { kind: "day", iso: cell.isoDate },
                e.clientX,
                e.clientY,
              );
            }
          : undefined
      }
    >
      <div className="hidden md:block">
        <span className={cn("mb-0.5", dayBase)}>{dayNum}</span>
        <div className="flex flex-col gap-0.5">
          {entries.map((entry, idx) => (
            <div
              key={entryKey(entry, cell.isoDate, idx)}
              onContextMenu={
                onContextMenu
                  ? (e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      onContextMenu(
                        {
                          kind: "entry",
                          entry,
                          label: resolveLabel(entry, seriesList),
                        },
                        e.clientX,
                        e.clientY,
                      );
                    }
                  : undefined
              }
            >
              {canManage && isDraggableEntry(entry) ? (
                <DraggableChip
                  {...chipPropsFor(entry, idx)}
                  seriesList={seriesList}
                />
              ) : (
                <Chip {...chipPropsFor(entry, idx)} seriesList={seriesList} />
              )}
            </div>
          ))}
        </div>
      </div>

      <button
        type="button"
        aria-label={dayLabel}
        aria-pressed={selected}
        onClick={onSelectDay}
        className={cn(
          "flex w-full flex-col items-center gap-1 rounded py-0.5 md:hidden",
          selected && "bg-primary/10",
        )}
      >
        <span className={dayBase}>{dayNum}</span>
        <span data-testid="day-dots" className="flex h-2 gap-0.5">
          {entries.slice(0, 4).map((entry, idx) => (
            <span
              key={entryKey(entry, cell.isoDate, idx)}
              className={cn("h-2 w-2 rounded-full", dotClass(entry))}
            />
          ))}
        </span>
      </button>
    </div>
  );
}
