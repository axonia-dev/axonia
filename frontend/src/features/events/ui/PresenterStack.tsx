import { Plus } from "lucide-react";
import { useEffect, useState } from "react";

import { currentUserId } from "@/auth/current-user";
import { Button } from "@/components/ui/Button";
import { useRemovePresenter } from "@/features/events/api/use-slot-presenters";
import { useWithdrawSlot } from "@/features/events/api/use-slot-sign-up";
import type { Slot } from "@/features/events/domain/slot";
import { eventsErrorMessage } from "@/features/events/events-errors";
import { AddPresenterPopover } from "@/features/events/ui/AddPresenterPopover";
import { PresenterHoverCard } from "@/features/events/ui/PresenterHoverCard";
import { displayNameOf } from "@/features/events/ui/SlotPresenter";
import type { User } from "@/features/identity/domain/user";

export interface PresenterStackProps {
  slug: string;
  sessionId: number;
  sectionId: number;
  slot: Slot;
  users: Record<string, User>;
  /** Organizer/policy: may add or remove anyone. */
  canManageSchedule: boolean;
  /** Whether any presenter interaction is on offer at all (false in readonly mode). */
  interactive: boolean;
}

/** The `after` box is a 44px hit area around a smaller circle. */
const addButtonBase =
  "relative grid shrink-0 cursor-pointer place-items-center rounded-full border-dashed transition-colors focus-visible:ring-focus-ring focus-visible:ring-offset-background focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none after:absolute after:top-1/2 after:size-11 after:-translate-y-1/2";

/** On an open slot the "+" is the whole stack and the slot's invitation, so it stands in the place
 * a face will take: the avatar's own `size-7 border-2`, in the accent. */
const inviteClass = `${addButtonBase} border-primary text-primary bg-surface hover:bg-primary/10 size-7 border-2 after:left-1/2 after:-translate-x-1/2`;

/** Beside faces it is a secondary action, so it is smaller and neutral until pointed at: at the
 * avatar's size and in the accent it read as one more presenter. Its hit area starts past the
 * `ml-1` gap and the border, where the last face ends, so it never covers that face, and the
 * `mr-2` reserves the rest of its width so it never covers the title either. */
const trailingClass = `${addButtonBase} border-border-strong text-muted-foreground hover:border-primary hover:text-primary hover:bg-primary/10 ml-1 mr-2 size-5 border after:-left-[calc(--spacing(1)+1px)]`;

/** An icon rather than a typed "+": centring a glyph centres its line box, and a plus sign sits
 * high in its own em box, so the typed one landed ~1px below the circle's centre. A vector plus is
 * centred on the box it is drawn in. */
function AddButton({
  trailing,
  onPress,
}: Readonly<{ trailing: boolean; onPress: () => void }>) {
  return (
    <button
      type="button"
      aria-label="Add presenter"
      onClick={onPress}
      className={trailing ? trailingClass : inviteClass}
    >
      <Plus
        aria-hidden
        className={trailing ? "size-3" : "size-4"}
        strokeWidth={2.5}
      />
    </button>
  );
}

interface ManagePopoverProps {
  name: string;
  isSelf: boolean;
  canManageSchedule: boolean;
  withdrawPending: boolean;
  removePending: boolean;
  error: string | null;
  onWithdraw: () => void;
  onRemove: () => void;
}

function ManagePopover({
  name,
  isSelf,
  canManageSchedule,
  withdrawPending,
  removePending,
  error,
  onWithdraw,
  onRemove,
}: Readonly<ManagePopoverProps>) {
  return (
    <div
      role="dialog"
      aria-label={`Manage ${name}`}
      className="border-border bg-surface absolute top-9 left-0 z-20 w-48 rounded-[var(--radius-md)] border p-3 shadow-[var(--shadow-lg)]"
    >
      <p className="text-foreground mb-2 text-sm font-medium">{name}</p>
      {isSelf && (
        <Button
          variant="ghost"
          size="sm"
          isDisabled={withdrawPending}
          onPress={onWithdraw}
        >
          Withdraw
        </Button>
      )}
      {!isSelf && canManageSchedule && (
        <Button
          variant="ghost"
          size="sm"
          isDisabled={removePending}
          onPress={onRemove}
        >
          Remove presenter
        </Button>
      )}
      {error && (
        <p role="alert" className="text-danger mt-2 text-xs">
          {error}
        </p>
      )}
    </div>
  );
}

/** Closes the currently open popover, if any, when Escape is pressed. */
function useCloseOnEscape(isOpen: boolean, onEscape: () => void) {
  useEffect(() => {
    if (!isOpen) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onEscape();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [isOpen, onEscape]);
}

/**
 * The avatar run for a slot's presenters, plus the "+" that opens the add/invite panel
 * and the small popover each avatar opens to manage that one presenter. Ownership lives
 * here rather than on SlotActions: joining, inviting, withdrawing, and removing are all
 * presenter-membership changes, not slot-editing actions.
 */
export function PresenterStack({
  slug,
  sessionId,
  sectionId,
  slot,
  users,
  canManageSchedule,
  interactive,
}: Readonly<PresenterStackProps>) {
  // The literal "add" documents the sentinel alongside an arbitrary presenter userId,
  // even though it is structurally a `string` too.
  // oxlint-disable-next-line typescript/no-redundant-type-constituents
  const [openPopover, setOpenPopover] = useState<"add" | string | null>(null);
  const me = currentUserId();
  const isPresenter = slot.presenterUserIds.includes(me ?? "");
  const withdraw = useWithdrawSlot(slug, sessionId, sectionId, slot.id);
  const removePresenter = useRemovePresenter(slug, sessionId);

  useCloseOnEscape(openPopover !== null, () => setOpenPopover(null));

  // Syncs local popover state to the mutation's async outcome: the popover closes once
  // the withdraw/remove actually lands, not the instant it is requested, so a failure
  // leaves it open with the error visible (see `manageError` below).
  useEffect(() => {
    if (withdraw.isSuccess || removePresenter.isSuccess) {
      // oxlint-disable-next-line react/set-state-in-effect
      setOpenPopover(null);
    }
  }, [withdraw.isSuccess, removePresenter.isSuccess]);

  // Only the viewer's own avatar, or any avatar for an organizer, opens a popover;
  // a plain member gets the hover card only on someone else's avatar.
  function canOpenManage(userId: string): boolean {
    return interactive && (userId === me || canManageSchedule);
  }

  // Whether the "+" is offered at all. It always is, while interactive: a viewer who
  // isn't presenting may add themself, and a viewer who is presenting (or manages the
  // schedule) may invite a co-presenter - between those two, every interactive viewer
  // has some action to offer, so this collapses to plain `interactive`.
  function showAdd(): boolean {
    return interactive;
  }

  function toggle(id: string) {
    setOpenPopover((prev) => (prev === id ? null : id));
  }

  function manageError(): string | null {
    if (withdraw.isError) {
      return eventsErrorMessage(withdraw.error);
    }
    if (removePresenter.isError) {
      return eventsErrorMessage(removePresenter.error);
    }
    return null;
  }

  if (slot.presenterUserIds.length === 0 && !showAdd()) {
    return null;
  }

  // The add panel hangs from the stack rather than the "+", and on a narrow card from the whole
  // header row, so it opens inside the card.
  return (
    <span className="relative flex items-center @max-md:static">
      <span className="flex -space-x-1.5">
        {slot.presenterUserIds.map((id) => {
          const name = displayNameOf(id, users);
          const openable = canOpenManage(id);
          return (
            // `flex`, not a bare block: the avatar inside is inline-level, so a plain wrapper
            // builds a line box around it and inherits the row's line-height. Wherever that
            // exceeds the 28px avatar the wrapper grows, `items-center` centres the taller box,
            // and the avatar rides the line box's top - dropping the "+" beside it by the
            // difference. A flex parent blockifies the child, so the wrapper is the avatar.
            <span key={id} className="relative flex">
              <PresenterHoverCard
                name={name}
                user={users[id]}
                suppressCard={openPopover === id}
                {...(openable ? { onPress: () => toggle(id) } : {})}
              />
              {openPopover === id && (
                <ManagePopover
                  name={name}
                  isSelf={id === me}
                  canManageSchedule={canManageSchedule}
                  withdrawPending={withdraw.isPending}
                  removePending={removePresenter.isPending}
                  error={manageError()}
                  onWithdraw={() => withdraw.mutate()}
                  onRemove={() =>
                    removePresenter.mutate({
                      sectionId,
                      slotId: slot.id,
                      userId: id,
                    })
                  }
                />
              )}
            </span>
          );
        })}
      </span>
      {showAdd() && (
        <span className="flex">
          <AddButton
            trailing={slot.presenterUserIds.length > 0}
            onPress={() => toggle("add")}
          />
          {openPopover === "add" && (
            <AddPresenterPopover
              slug={slug}
              sessionId={sessionId}
              sectionId={sectionId}
              slotId={slot.id}
              presenterUserIds={slot.presenterUserIds}
              alreadyPresenting={isPresenter}
              canPickOthers={canManageSchedule || isPresenter}
              onClose={() => setOpenPopover(null)}
            />
          )}
        </span>
      )}
    </span>
  );
}
