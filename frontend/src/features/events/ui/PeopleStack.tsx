import { Plus } from "lucide-react";
import { type ReactNode, useEffect, useState } from "react";

import { currentUserId } from "@/auth/current-user";
import { Button } from "@/components/ui/Button";
import { eventsErrorMessage } from "@/features/events/events-errors";
import { displayNameOf } from "@/features/events/ui/SlotPresenter";
import type { User } from "@/features/identity/domain/user";

/** A membership change the stack can trigger: withdrawing the viewer, or removing someone. */
export interface PeopleAction<A extends unknown[]> {
  mutate: (...args: A) => void;
  isPending: boolean;
  isSuccess: boolean;
  /** `null` while the last attempt did not fail. */
  error: unknown;
}

interface MutationState {
  isPending: boolean;
  isSuccess: boolean;
  isError: boolean;
  error: unknown;
}

export function peopleAction<A extends unknown[]>(
  state: MutationState,
  mutate: (...args: A) => void,
): PeopleAction<A> {
  return {
    mutate,
    isPending: state.isPending,
    isSuccess: state.isSuccess,
    error: state.isError ? state.error : null,
  };
}

/** What the stack hands each face: a face is a button only when `onPress` is given. */
export interface FaceProps {
  name: string;
  user: User | undefined;
  suppressCard: boolean;
  onPress?: () => void;
}

type Noun = "presenter" | "speaker";

export interface PeopleStackProps {
  userIds: string[];
  users: Record<string, User>;
  noun: Noun;
  /** May add or remove anyone, not only the viewer. */
  canManageOthers: boolean;
  /** Whether any interaction is on offer at all (false in readonly mode). */
  interactive: boolean;
  /** False when the picker would hold nothing for this viewer, so the "+" is not offered. */
  canAdd?: boolean;
  /** The "+"'s accessible name, `Add <noun>` unless the caller names what it adds to. */
  addLabel?: string;
  renderFace: (userId: string, face: FaceProps) => ReactNode;
  withdraw: PeopleAction<[]>;
  remove: PeopleAction<[userId: string]>;
  renderPicker: (onClose: () => void) => ReactNode;
}

/** The `after` box is a 44px hit area around a smaller circle. */
const addButtonBase =
  "relative grid shrink-0 cursor-pointer place-items-center rounded-full border-dashed transition-colors focus-visible:ring-focus-ring focus-visible:ring-offset-background focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:outline-none after:absolute after:top-1/2 after:size-11 after:-translate-y-1/2";

/** With nobody yet the "+" is the whole stack and the invitation, so it stands in the place a
 * face will take: the avatar's own `size-7 border-2`, in the accent. */
const inviteClass = `${addButtonBase} border-primary text-primary bg-surface hover:bg-primary/10 size-7 border-2 after:left-1/2 after:-translate-x-1/2`;

/** Beside faces it is a secondary action, so it is smaller and neutral until pointed at: at the
 * avatar's size and in the accent it read as one more person. Its hit area starts past the
 * `ml-1` gap and the border, where the last face ends, so it never covers that face, and the
 * `mr-2` reserves the rest of its width so it never covers the title either. */
const trailingClass = `${addButtonBase} border-border-strong text-muted-foreground hover:border-primary hover:text-primary hover:bg-primary/10 ml-1 mr-2 size-5 border after:-left-[calc(--spacing(1)+1px)]`;

/** An icon rather than a typed "+": centring a glyph centres its line box, and a plus sign sits
 * high in its own em box, so the typed one landed ~1px below the circle's centre. A vector plus is
 * centred on the box it is drawn in. */
function AddButton({
  label,
  trailing,
  onPress,
}: Readonly<{ label: string; trailing: boolean; onPress: () => void }>) {
  return (
    <button
      type="button"
      aria-label={label}
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
  noun: Noun;
  isSelf: boolean;
  canManageOthers: boolean;
  withdraw: PeopleAction<[]>;
  remove: PeopleAction<[userId: string]>;
  onRemove: () => void;
}

function manageError(
  withdraw: PeopleAction<[]>,
  remove: PeopleAction<[userId: string]>,
): string | null {
  const error = withdraw.error ?? remove.error;
  return error ? eventsErrorMessage(error) : null;
}

function ManagePopover({
  name,
  noun,
  isSelf,
  canManageOthers,
  withdraw,
  remove,
  onRemove,
}: Readonly<ManagePopoverProps>) {
  const error = manageError(withdraw, remove);
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
          isDisabled={withdraw.isPending}
          onPress={() => withdraw.mutate()}
        >
          Withdraw
        </Button>
      )}
      {!isSelf && canManageOthers && (
        <Button
          variant="ghost"
          size="sm"
          isDisabled={remove.isPending}
          onPress={onRemove}
        >
          Remove {noun}
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
 * The avatar run for the people presenting something, plus the "+" that opens the add/invite
 * panel and the small popover each avatar opens to manage that one person. A slot's presenters
 * and a topic's speakers are the same membership with the same rules, so both draw this.
 */
export function PeopleStack({
  userIds,
  users,
  noun,
  canManageOthers,
  interactive,
  canAdd = true,
  addLabel = `Add ${noun}`,
  renderFace,
  withdraw,
  remove,
  renderPicker,
}: Readonly<PeopleStackProps>) {
  // The literal "add" documents the sentinel alongside an arbitrary userId,
  // even though it is structurally a `string` too.
  // oxlint-disable-next-line typescript/no-redundant-type-constituents
  const [openPopover, setOpenPopover] = useState<"add" | string | null>(null);
  const me = currentUserId();
  const close = () => setOpenPopover(null);

  useCloseOnEscape(openPopover !== null, close);

  // Syncs local popover state to the mutation's async outcome: the popover closes once
  // the withdraw/remove actually lands, not the instant it is requested, so a failure
  // leaves it open with the error visible.
  useEffect(() => {
    if (withdraw.isSuccess || remove.isSuccess) {
      // oxlint-disable-next-line react/set-state-in-effect
      setOpenPopover(null);
    }
  }, [withdraw.isSuccess, remove.isSuccess]);

  // Only the viewer's own avatar, or any avatar for whoever manages others, opens a popover;
  // a plain member gets the hover card only on someone else's avatar.
  function canOpenManage(userId: string): boolean {
    return interactive && (userId === me || canManageOthers);
  }

  function toggle(id: string) {
    setOpenPopover((prev) => (prev === id ? null : id));
  }

  const showAdd = interactive && canAdd;
  if (userIds.length === 0 && !showAdd) {
    return null;
  }

  // The add panel hangs from the stack rather than the "+", and on a narrow card from the whole
  // header row, so it opens inside the card.
  return (
    <span className="relative flex items-center @max-md:static">
      <span className="flex -space-x-1.5">
        {userIds.map((id) => {
          const name = displayNameOf(id, users);
          return (
            // `flex`, not a bare block: the avatar inside is inline-level, so a plain wrapper
            // builds a line box around it and inherits the row's line-height. Wherever that
            // exceeds the 28px avatar the wrapper grows, `items-center` centres the taller box,
            // and the avatar rides the line box's top - dropping the "+" beside it by the
            // difference. A flex parent blockifies the child, so the wrapper is the avatar.
            <span key={id} className="relative flex">
              {renderFace(id, {
                name,
                user: users[id],
                suppressCard: openPopover === id,
                ...(canOpenManage(id) ? { onPress: () => toggle(id) } : {}),
              })}
              {openPopover === id && (
                <ManagePopover
                  name={name}
                  noun={noun}
                  isSelf={id === me}
                  canManageOthers={canManageOthers}
                  withdraw={withdraw}
                  remove={remove}
                  onRemove={() => remove.mutate(id)}
                />
              )}
            </span>
          );
        })}
      </span>
      {showAdd && (
        <span className="flex">
          <AddButton
            label={addLabel}
            trailing={userIds.length > 0}
            onPress={() => toggle("add")}
          />
          {openPopover === "add" && renderPicker(close)}
        </span>
      )}
    </span>
  );
}
