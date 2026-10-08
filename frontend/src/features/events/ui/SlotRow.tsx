import { type ReactNode } from "react";

import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import { sessionTitle } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { PresenterStack } from "@/features/events/ui/PresenterStack";
import { RemoveSlotPopover } from "@/features/events/ui/RemoveSlotPopover";
import {
  isShapingMode,
  type SessionMode,
} from "@/features/events/ui/session-mode";
import { SlotActions } from "@/features/events/ui/SlotActions";
import { SlotBody } from "@/features/events/ui/SlotBody";
import { SlotDialog } from "@/features/events/ui/SlotDialog";
import { SlotMeta } from "@/features/events/ui/SlotMeta";
import { presenterByline } from "@/features/events/ui/SlotPresenter";
import { useSlotRow } from "@/features/events/ui/use-slot-row";
import type { User } from "@/features/identity/domain/user";
import { cn } from "@/lib/cn";

interface Props {
  slug: string;
  session: Session;
  section: Section;
  slot: Slot;
  actions?: ReactNode;
  mode?: SessionMode;
  canManage?: boolean;
  canManageSchedule?: boolean;
  users?: Record<string, User>;
}

/** The slot's own title always wins; whether it has presenters yet is irrelevant to what it's
 * called. Only a slot that was never given one falls back to "Open slot". */
function slotTitle(slot: Slot): string {
  return slot.title ?? "Open slot";
}

/**
 * The immutable snapshot of what this slot was, taken when a resource is captured against it:
 * the title, and nothing else. It deliberately carries no presenter suffix and therefore no
 * separator - a label of the form `"<title> - <presenters>"` cannot be parsed back, because a
 * title like "Kafka - the good parts" contains the separator itself and would be truncated on
 * display. Who presented is already on the card, as the artifact's contributor.
 */
function provenanceLabel(slot: Slot): string {
  return slot.title ?? "Slot";
}

/** What a captured resource defaults to when nobody names it: the slot's own title, or - for
 *  an open slot - the session's, so a backfilled recording is never left called nothing. */
function slotDefaultTitle(slot: Slot, session: Session): string {
  return slot.title ?? sessionTitle(session);
}

const pillClass =
  "text-foreground bg-surface border-border rounded-full border px-2.5 py-1 text-xs font-semibold whitespace-nowrap tabular-nums @max-md:col-start-2 @max-md:justify-self-end";

interface SlotHeaderRowProps {
  slug: string;
  session: Session;
  section: Section;
  slot: Slot;
  users: Record<string, User>;
  canManageSchedule: boolean;
  interactive: boolean;
  shaping: boolean;
  isPresenter: boolean;
  removePending: boolean;
  onEdit: () => void;
  onRemove: () => void;
}

/** The card's first row: the presenter stack, the title, the hover-revealed edit/remove
 * cluster (only while the mode gives the row structural affordances), and the duration pill. On a
 * narrow card the title and the pill take a second line, below the stack and the cluster when
 * there are any. */
function SlotHeaderRow({
  slug,
  session,
  section,
  slot,
  users,
  canManageSchedule,
  interactive,
  shaping,
  isPresenter,
  removePending,
  onEdit,
  onRemove,
}: Readonly<SlotHeaderRowProps>) {
  return (
    <div className="flex items-center gap-3 @max-md:relative @max-md:grid @max-md:grid-cols-[minmax(0,1fr)_auto] @max-md:gap-y-1.5">
      <PresenterStack
        slug={slug}
        sessionId={session.id}
        sectionId={section.id}
        slot={slot}
        users={users}
        canManageSchedule={canManageSchedule}
        interactive={interactive}
      />
      <span className="text-foreground min-w-0 flex-1 truncate text-sm font-medium @max-md:col-start-1">
        {slotTitle(slot)}
      </span>
      {shaping && (
        <SlotActions
          canManageSlot={isPresenter || canManageSchedule}
          removePending={removePending}
          onEdit={onEdit}
          onRemove={onRemove}
        />
      )}
      <span className={pillClass}>{slot.durationMinutes} min</span>
    </div>
  );
}

export function SlotRow({
  slug,
  session,
  section,
  slot,
  actions,
  mode = "contribute",
  canManage = false,
  canManageSchedule = false,
  users = {},
}: Readonly<Props>) {
  const {
    remove,
    editing,
    setEditing,
    removing,
    setRemoving,
    leaving,
    errorMessage,
    isPresenter,
    openEditing,
    beginRemove,
  } = useSlotRow(slug, session.id, section.id, slot);

  const capture = mode === "capture";
  const shaping = isShapingMode(mode);
  const byline = presenterByline(slot.presenterUserIds, users);

  return (
    <div
      className={cn(
        "group border-border bg-surface-sunken @container relative flex flex-col gap-2 rounded-md border p-3",
        "motion-safe:animate-[slot-in_220ms_var(--ease-standard,ease-out)]",
        // Raise the hovered/focused row above later sibling rows so the presenter
        // hover card is not overpainted by the next slot: every row is positioned,
        // so without this the following (later-in-DOM) one paints over this one.
        "focus-within:z-10 hover:z-10",
        // The row answers the pointer with its border alone. It is not itself a
        // target - the controls inside it are - so anything that moves it or lifts
        // it off the sheet promises a click that does nothing.
        "hover:border-border-strong motion-safe:transition-colors",
        leaving &&
          "overflow-hidden motion-safe:animate-[slot-collapse_200ms_var(--ease-standard,ease-out)_forwards]",
      )}
    >
      <SlotHeaderRow
        slug={slug}
        session={session}
        section={section}
        slot={slot}
        users={users}
        canManageSchedule={canManageSchedule}
        interactive={mode !== "readonly"}
        shaping={shaping}
        isPresenter={isPresenter}
        removePending={remove.isPending}
        onEdit={openEditing}
        onRemove={() => setRemoving(true)}
      />

      {byline && <p className="text-muted-foreground text-xs">{byline}</p>}

      <SlotMeta slot={slot} />

      <SlotBody
        slug={slug}
        session={session}
        slot={slot}
        canManage={canManage}
        canAdd={shaping ? canManageSchedule : capture}
        provenanceLabel={provenanceLabel(slot)}
        defaultTitle={slotDefaultTitle(slot, session)}
        presenterUserIds={slot.presenterUserIds}
        actions={actions}
      />
      {errorMessage && (
        <p role="alert" className="text-danger text-sm">
          {errorMessage}
        </p>
      )}

      <SlotDialog
        mode="edit"
        slug={slug}
        session={session}
        section={section}
        slot={slot}
        isOpen={editing}
        onOpenChange={setEditing}
      />

      <RemoveSlotPopover
        slotLabel={slotTitle(slot)}
        isOpen={removing}
        isPending={remove.isPending}
        onConfirm={beginRemove}
        onOpenChange={setRemoving}
      />
    </div>
  );
}
