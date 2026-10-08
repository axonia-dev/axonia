import { Plus } from "lucide-react";

import type { ScheduleEntry } from "@/features/events/domain/schedule";
import {
  isRoundTable,
  sectionAccentColor,
} from "@/features/events/domain/section";
import type { Slot } from "@/features/events/domain/slot";
import { isOpen } from "@/features/events/domain/slot";
import { PresenterAvatars } from "@/features/events/ui/PresenterAvatars";
import { displayNameOf } from "@/features/events/ui/SlotPresenter";
import type { User } from "@/features/identity/domain/user";

type AgendaSection = ScheduleEntry["sections"][number];

function presenterLine(slot: Slot, users: Record<string, User>): string {
  return slot.presenterUserIds.map((id) => displayNameOf(id, users)).join(", ");
}

function SlotRow({
  slot,
  users,
}: Readonly<{ slot: Slot; users: Record<string, User> }>) {
  const open = isOpen(slot);
  return (
    <li className="flex items-start gap-2.5 py-1.5 pl-2.5">
      {open ? (
        <span
          aria-hidden="true"
          className="border-border-strong text-muted-foreground grid size-7 shrink-0 place-items-center rounded-full border-2 border-dashed"
        >
          <Plus className="size-4" strokeWidth={2.5} />
        </span>
      ) : (
        <PresenterAvatars userIds={slot.presenterUserIds} users={users} />
      )}
      <span className="min-w-0 flex-1">
        <span
          className={
            open
              ? "text-muted-foreground block text-sm"
              : "text-foreground block text-sm"
          }
        >
          {open ? "Open spot" : (slot.title ?? "A talk")}
        </span>
        {!open && (
          <span className="text-muted-foreground block text-xs">
            {presenterLine(slot, users)}
          </span>
        )}
        {slot.tags.length > 0 && (
          <span className="mt-1 flex flex-wrap gap-1">
            {slot.tags.map((tag) => (
              <span
                key={tag}
                className="bg-surface-sunken text-muted-foreground rounded px-1.5 py-0.5 text-[10px]"
              >
                {tag}
              </span>
            ))}
          </span>
        )}
      </span>
      <span className="text-muted-foreground shrink-0 text-xs tabular-nums">
        {slot.durationMinutes} min
      </span>
    </li>
  );
}

function SectionGroup({
  section,
  users,
}: Readonly<{ section: AgendaSection; users: Record<string, User> }>) {
  const accent = sectionAccentColor(section);
  return (
    <li className="mb-3 last:mb-0">
      <span className="mb-1 flex items-center gap-2">
        <span
          aria-hidden="true"
          className="h-3 w-[3px] shrink-0 rounded-sm"
          style={{ backgroundColor: accent }}
        />
        <span className="text-foreground text-xs font-semibold">
          {section.sectionType ?? section.title ?? "Agenda"}
        </span>
        {section.durationMinutes !== null && (
          <span className="text-muted-foreground ml-auto text-xs tabular-nums">
            {section.durationMinutes} min
          </span>
        )}
      </span>
      {isRoundTable(section) ? (
        <p className="text-muted-foreground pl-2.5 text-sm">
          A round table, with no talks scheduled
        </p>
      ) : (
        <ul>
          {section.slots.map((slot) => (
            <SlotRow key={slot.id} slot={slot} users={users} />
          ))}
        </ul>
      )}
    </li>
  );
}

export function SessionAgenda({
  entry,
  users,
}: Readonly<{ entry: ScheduleEntry; users: Record<string, User> }>) {
  if (entry.sections.length === 0) {
    return null;
  }
  return (
    <div className="border-border border-t pt-3">
      <h3 className="text-muted-foreground mb-2 text-[10px] font-bold tracking-widest uppercase">
        On the agenda
      </h3>
      <ul>
        {entry.sections.map((section) => (
          <SectionGroup
            key={section.id ?? section.position}
            section={section}
            users={users}
          />
        ))}
      </ul>
    </div>
  );
}
