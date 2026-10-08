import { type ReactNode, useMemo } from "react";

import { SortableList } from "@/components/ui/SortableList";
import { useReorderSlots } from "@/features/events/api/use-slot-sign-up";
import { sectionRunMinutes } from "@/features/events/domain/run-sheet";
import type { Section } from "@/features/events/domain/section";
import { isRoundTable } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { AddToSectionForm } from "@/features/events/ui/AddToSectionForm";
import { BudgetBar } from "@/features/events/ui/BudgetBar";
import { SectionTypeIcon } from "@/features/events/ui/section-type-visual";
import {
  isShapingMode,
  type SessionMode,
} from "@/features/events/ui/session-mode";
import { SlotRow } from "@/features/events/ui/SlotRow";
import { useUsers } from "@/features/identity/api/use-users";
import { cn } from "@/lib/cn";

interface Props {
  slug: string;
  session: Session;
  section: Section;
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

function sectionHeading(section: Section): string {
  const sectionType = section.sectionType ?? "Section";
  // The non-breaking space keeps "·" with the title, so a narrow row never ends a line on it.
  return section.title ? `${sectionType} ·\u00a0${section.title}` : sectionType;
}

/** Whether a section's duration is a real budget to hold slots to, rather than its own length. */
function hasBudget(section: Section): boolean {
  return section.durationMinutes !== null && !isRoundTable(section);
}

/**
 * What a section says about its own time, in the two registers it has.
 *
 * A budget is a guardrail on an edit, so it is stated while the structure can still be shaped and
 * silent once it cannot: on a held session being read, the section simply says how long it ran. A
 * round table has no guardrail to state - its duration is not a cap on something else, so it is
 * always read as plain minutes, never as a ratio against itself.
 */
function meterLabel(section: Section, shaping: boolean): string {
  const used = sectionRunMinutes(section);
  if (shaping && hasBudget(section)) {
    return `${used} / ${section.durationMinutes} min`;
  }
  return `${used} min`;
}

/** The meter itself, present only while the budget it guards is both real and still shapeable. */
function SectionMeter({
  section,
  shaping,
}: Readonly<{ section: Section; shaping: boolean }>) {
  if (!shaping || !hasBudget(section)) {
    return null;
  }
  return (
    <BudgetBar
      usedMinutes={sectionRunMinutes(section)}
      budgetMinutes={section.durationMinutes}
      className="[&>p]:sr-only"
    />
  );
}

/** Whether an organizer shaping the session may drag its slots into another order. */
function isSortable(
  shaping: boolean,
  canManage: boolean,
  section: Section,
): boolean {
  return shaping && canManage && section.slots.length > 1;
}

/** The indent holds the reorder handles, so a phone gives it back to the cards when there are none. */
function slotListClass(sortable: boolean): string {
  return cn("flex flex-col gap-2 pl-5", !sortable && "max-sm:pl-0");
}

export function SectionBlock({
  slug,
  session,
  section,
  canManage = false,
  canManageSchedule = false,
  canQueueTopics = false,
  mode = "contribute",
  renderSlotActions,
}: Readonly<Props>) {
  const reorder = useReorderSlots(slug, session.id, section.id);
  const ids = section.slots.map((s) => String(s.id));
  const shaping = isShapingMode(mode);
  const sortable = isSortable(shaping, canManage, section);

  const directoryIds = useMemo(
    () => Array.from(new Set(section.slots.flatMap((s) => s.presenterUserIds))),
    [section.slots],
  );
  const users = useUsers(directoryIds).data ?? {};

  function renderSlotList() {
    if (sortable) {
      return (
        <SortableList
          ids={ids}
          onReorder={(next) => reorder.mutate(next.map(Number))}
        >
          {(id, handle) => {
            const slot = section.slots.find((s) => String(s.id) === id);
            if (!slot) {
              return null;
            }
            const index = ids.indexOf(id);
            return (
              <div className="relative">
                <button
                  type="button"
                  aria-label={`Reorder slot ${index + 1}`}
                  className="text-border-strong absolute top-3 -left-5 w-5 cursor-grab touch-none text-center select-none"
                  {...handle}
                >
                  ⠿
                </button>
                <SlotRow
                  slug={slug}
                  session={session}
                  section={section}
                  slot={slot}
                  mode={mode}
                  canManage={canManage}
                  canManageSchedule={canManageSchedule}
                  users={users}
                  {...(renderSlotActions
                    ? { actions: renderSlotActions(slot, section, session) }
                    : {})}
                />
              </div>
            );
          }}
        </SortableList>
      );
    }
    return (
      <ul className="flex flex-col gap-2">
        {section.slots.map((slot) => (
          <li key={slot.id}>
            <SlotRow
              slug={slug}
              session={session}
              section={section}
              slot={slot}
              mode={mode}
              canManage={canManage}
              canManageSchedule={canManageSchedule}
              users={users}
              {...(renderSlotActions
                ? { actions: renderSlotActions(slot, section, session) }
                : {})}
            />
          </li>
        ))}
      </ul>
    );
  }

  return (
    <section className="flex flex-col gap-2">
      <h4 className="text-foreground flex items-center gap-3 text-sm font-semibold">
        {section.sectionTypeId !== null && (
          <SectionTypeIcon
            typeId={section.sectionTypeId}
            label={section.sectionType ?? ""}
            icon={section.sectionTypeIcon}
            accentColor={section.sectionTypeAccentColor}
            size={16}
          />
        )}
        <span className="min-w-0 break-words">{sectionHeading(section)}</span>
        <span className="bg-border h-px flex-1" aria-hidden />
        <span className="text-muted-foreground shrink-0 text-xs tabular-nums">
          {meterLabel(section, shaping)}
        </span>
      </h4>
      <SectionMeter section={section} shaping={shaping} />
      <div className={slotListClass(sortable)}>
        {isRoundTable(section) ? (
          <p className="text-muted-foreground text-sm">
            No presenter, open discussion
          </p>
        ) : (
          renderSlotList()
        )}
        {shaping && (
          <AddToSectionForm
            slug={slug}
            session={session}
            section={section}
            canQueueTopics={canQueueTopics}
            mode={mode}
          />
        )}
      </div>
    </section>
  );
}
