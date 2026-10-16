import type { Memory } from "@/features/events/domain/memory";
import { agoLabel, memoryFade } from "@/features/events/domain/memory";
import { entryDayIso } from "@/features/events/domain/occurrence-day";
import { MemoryCard } from "@/features/events/ui/timeline/MemoryCard";
import {
  dateParts,
  entrySortKey,
} from "@/features/events/ui/timeline/timeline";
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

export function MemoryRow({
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
      data-at={new Date(entrySortKey(entry)).getTime()}
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
