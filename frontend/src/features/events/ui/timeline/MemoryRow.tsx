import { memo } from "react";

import type { Memory } from "@/features/events/domain/memory";
import { agoLabel, memoryFade } from "@/features/events/domain/memory";
import { entryDayIso } from "@/features/events/domain/occurrence-day";
import { MemoryCard } from "@/features/events/ui/timeline/MemoryCard";
import { dateParts, entryAtMs } from "@/features/events/ui/timeline/timeline";
import {
  DateCell,
  RailDot,
  rowGridClass,
} from "@/features/events/ui/timeline/TimelineRow";
import { cn } from "@/lib/cn";

interface Props {
  memory: Memory;
  label: string;
  href: string;
  todayIso: string;
  canManage: boolean;
  arriving?: boolean;
}

function MemoryRowView({
  memory,
  label,
  href,
  todayIso,
  canManage,
  arriving = false,
}: Readonly<Props>) {
  const { entry } = memory;
  const iso = entryDayIso(entry);
  return (
    <li
      className={cn(
        "timeline-row group relative pb-3 focus-within:z-10 hover:z-10",
        rowGridClass,
        arriving && "memory-arrive",
      )}
      data-at={entryAtMs(entry)}
    >
      <div className="pt-2.5 pr-1.5 text-right">
        {iso !== "" && (
          <DateCell
            parts={dateParts(iso, todayIso)}
            ghost={false}
            caption={agoLabel(iso, todayIso)}
            tone="memory"
          />
        )}
      </div>
      <div className="relative h-full" aria-hidden="true">
        <RailDot ghost={false} nextUp={false} variant="memory" />
      </div>
      <MemoryCard
        memory={memory}
        label={label}
        href={href}
        canManage={canManage}
        fade={iso === "" ? 1 : memoryFade(iso, todayIso)}
      />
    </li>
  );
}

const sameRow = (a: Readonly<Props>, b: Readonly<Props>) =>
  a.memory.entry === b.memory.entry &&
  a.memory.traces === b.memory.traces &&
  a.label === b.label &&
  a.href === b.href &&
  a.todayIso === b.todayIso &&
  a.canManage === b.canManage &&
  a.arriving === b.arriving;

/** A memory the page re-derives is a new object each render, so rows compare what they show. */
export const MemoryRow = memo(MemoryRowView, sameRow);
