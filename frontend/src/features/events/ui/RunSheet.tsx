import type { CSSProperties, ReactNode } from "react";

import type { NowMarker } from "@/features/events/domain/now-marker";
import { nowMarkerState } from "@/features/events/domain/now-marker";
import type { RunSheetRow } from "@/features/events/domain/run-sheet";
import { runSheet, sectionAnchorId } from "@/features/events/domain/run-sheet";
import type { Section } from "@/features/events/domain/section";
import { sectionAccentColor } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { NowMark } from "@/features/events/ui/NowMark";
import { PlannedEndMarker } from "@/features/events/ui/PlannedEndMarker";
import { SectionBlock } from "@/features/events/ui/SectionBlock";
import type { SessionMode } from "@/features/events/ui/session-mode";
import { useNow, useWakeAt } from "@/lib/use-wake-at";

interface Props {
  slug: string;
  session: Session;
  canManage?: boolean;
  canManageSchedule?: boolean;
  canQueueTopics?: boolean;
  mode?: SessionMode;
  renderSlotActions?: (
    slot: Slot,
    section: Section,
    session: Session,
  ) => ReactNode;
}

/** Pixels a minute. A floor, never a ceiling: content taller than its share simply grows. */
const PER_MINUTE = 2.6;
const FLOOR = 44;
const CEILING = 220;

function minHeight(runMinutes: number): string {
  return `${Math.round(Math.min(CEILING, Math.max(FLOOR, runMinutes * PER_MINUTE)))}px`;
}

/** How much of a row the session has already spent, from 0 for one still to come to 1 for one
 *  wholly behind the clock. Always 0 when nothing is running: an agenda with no mark on it is
 *  drawn in full. */
function spentOf(marker: NowMarker | null, index: number): number {
  if (marker === null || index > marker.rowIndex) return 0;
  return index < marker.rowIndex ? 1 : marker.fraction;
}

/** The rail beside a row, faded over the part of it the session has already run through. */
function bandBackground(color: string, spent: number): string {
  const faded = `color-mix(in srgb, ${color} 34%, transparent)`;
  if (spent <= 0) return color;
  if (spent >= 1) return faded;
  const at = `${(spent * 100).toFixed(1)}%`;
  return `linear-gradient(to bottom, ${faded} 0 ${at}, ${color} ${at} 100%)`;
}

function bandStyle(row: RunSheetRow, spent: number): CSSProperties {
  if (row.pastPlannedEnd) {
    return {
      background: bandBackground("var(--warning)", spent),
      opacity: 0.55,
    };
  }
  return {
    background: bandBackground(sectionAccentColor(row.section), spent),
    opacity: 0.85,
  };
}

export function RunSheet({
  slug,
  session,
  canManage = false,
  canManageSchedule = false,
  canQueueTopics = false,
  mode = "contribute",
  renderSlotActions,
}: Readonly<Props>) {
  const sheet = runSheet(session);
  const { marker, wakeAt } = nowMarkerState(session, sheet.rows, useNow());
  useWakeAt(wakeAt);
  if (sheet.rows.length === 0) {
    return null;
  }
  return (
    <ol
      aria-label="Run sheet"
      className="grid grid-cols-[3.25rem_4px_minmax(0,1fr)] gap-x-3.5 max-sm:grid-cols-[2.5rem_4px_minmax(0,1fr)] max-sm:gap-x-2"
    >
      {sheet.rows.map((row, index) => (
        <RowGroup
          key={row.section.id}
          row={row}
          spent={spentOf(marker, index)}
          now={marker?.rowIndex === index ? marker : null}
          marker={
            sheet.plannedEndAfter === index ? (
              <PlannedEndMarker
                label={sheet.plannedEndLabel ?? ""}
                overrunMinutes={sheet.overrunMinutes}
              />
            ) : null
          }
        >
          <SectionBlock
            slug={slug}
            session={session}
            section={row.section}
            canManage={canManage}
            canManageSchedule={canManageSchedule}
            canQueueTopics={canQueueTopics}
            mode={mode}
            {...(renderSlotActions ? { renderSlotActions } : {})}
          />
        </RowGroup>
      ))}
      {sheet.plannedEndAfter === sheet.rows.length && (
        <PlannedEndMarker
          label={sheet.plannedEndLabel ?? ""}
          overrunMinutes={sheet.overrunMinutes}
        />
      )}
    </ol>
  );
}

/**
 * One block on the sheet: the hour it starts at, the band that gives its type a silhouette, and
 * the section itself. The band is a sibling rather than a border so it can carry the accent
 * without the body inheriting a padding it did not ask for.
 */
function RowGroup({
  row,
  spent,
  now,
  marker,
  children,
}: Readonly<{
  row: RunSheetRow;
  spent: number;
  now: NowMarker | null;
  marker: ReactNode;
  children: ReactNode;
}>) {
  return (
    <>
      {marker}
      <li className="relative col-span-full grid grid-cols-subgrid">
        <span className="text-muted-foreground pt-0.5 text-right text-xs font-semibold tabular-nums">
          {row.startLabel}
        </span>
        <span
          aria-hidden
          data-part="band"
          className="rounded-full"
          style={bandStyle(row, spent)}
        />
        <div
          id={sectionAnchorId(row.section.id)}
          data-part="body"
          className="scroll-mt-24 pb-4"
          style={{ minHeight: minHeight(row.runMinutes) }}
        >
          {children}
        </div>
        {now && <NowMark marker={now} />}
      </li>
    </>
  );
}
