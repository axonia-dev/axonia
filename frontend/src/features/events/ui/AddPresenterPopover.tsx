import { useEffect, useState } from "react";

import { Button } from "@/components/ui/Button";
import { TextInput } from "@/components/ui/TextInput";
import { useMembers } from "@/features/community/api/use-membership";
import { useAddPresenter } from "@/features/events/api/use-slot-presenters";
import { usePresentSlot } from "@/features/events/api/use-slot-sign-up";
import { eventsErrorMessage } from "@/features/events/events-errors";
import { displayNameOf } from "@/features/events/ui/SlotPresenter";
import { useUsers } from "@/features/identity/api/use-users";

export interface AddPresenterPopoverProps {
  slug: string;
  sessionId: number;
  sectionId: number;
  slotId: number;
  /** Excluded from the roster: already presenting this slot. */
  presenterUserIds: string[];
  /** Hides the self sign-up row: the viewer already presents this slot. */
  alreadyPresenting: boolean;
  /** Organizer, or a current co-presenter inviting a collaborator to their own talk. */
  canPickOthers: boolean;
  onClose: () => void;
}

const ROSTER_PARAMS = { page: 0, size: 100 } as const;

const NOT_MATCHED_MESSAGE =
  "That person could not be matched to a member of this community.";

function rosterCandidates(
  rosterIds: string[],
  presenterUserIds: string[],
): string[] {
  return rosterIds.filter((id) => !presenterUserIds.includes(id));
}

function matchesQuery(name: string, query: string): boolean {
  return name.toLowerCase().includes(query.trim().toLowerCase());
}

function SelfSignUpRow({
  isPending,
  onPress,
}: Readonly<{ isPending: boolean; onPress: () => void }>) {
  return (
    <div className="border-border flex items-center justify-between gap-3 border-b pb-2">
      <span className="text-foreground text-sm font-medium">Me</span>
      <Button
        variant="ghost"
        size="sm"
        isDisabled={isPending}
        onPress={onPress}
      >
        I&apos;ll present this
      </Button>
    </div>
  );
}

interface RosterProps {
  candidateIds: string[];
  isPending: boolean;
  onPick: (userId: string) => void;
}

function Roster({ candidateIds, isPending, onPick }: Readonly<RosterProps>) {
  const [query, setQuery] = useState("");
  const directory = useUsers(candidateIds).data ?? {};
  const visible = candidateIds.filter((id) =>
    matchesQuery(displayNameOf(id, directory), query),
  );

  return (
    <div className="flex flex-col gap-2">
      <TextInput
        label="Search members"
        value={query}
        onChange={setQuery}
        placeholder="Search members"
      />
      <ul className="relative flex max-h-48 flex-col gap-0.5 overflow-y-auto">
        {visible.map((id) => (
          <li key={id}>
            <button
              type="button"
              disabled={isPending}
              onClick={() => onPick(id)}
              className="hover:bg-surface-sunken text-foreground w-full cursor-pointer rounded px-2 py-1.5 text-left text-sm disabled:cursor-not-allowed disabled:opacity-50"
            >
              {displayNameOf(id, directory)}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * The panel opened by the avatar stack's "+": a pinned self sign-up row, and, for
 * whoever may invite a collaborator (an organizer, or any current presenter of this
 * slot), a filterable roster of the rest of the community.
 */
export function AddPresenterPopover({
  slug,
  sessionId,
  sectionId,
  slotId,
  presenterUserIds,
  alreadyPresenting,
  canPickOthers,
  onClose,
}: Readonly<AddPresenterPopoverProps>) {
  const [pickError, setPickError] = useState<string | null>(null);
  const present = usePresentSlot(slug, sessionId, sectionId, slotId);
  const addPresenter = useAddPresenter(slug, sessionId);
  const members = useMembers(slug, ROSTER_PARAMS);
  const roster = rosterCandidates(
    (members.data?.content ?? []).map((member) => member.userId),
    presenterUserIds,
  );

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  // Closes only once the sign-up actually lands: a failure keeps the popover
  // open with the error visible below, same as a roster pick.
  function handleSelfSignUp() {
    present.mutate(undefined, { onSuccess: onClose });
  }

  async function handlePick(userId: string) {
    setPickError(null);
    try {
      const result = await addPresenter.mutateAsync({
        sectionId,
        slotId,
        presenter: userId,
      });
      if (!result.assigned) {
        setPickError(NOT_MATCHED_MESSAGE);
        return;
      }
      onClose();
    } catch {
      // addPresenter.isError already reflects this below; nothing more to do.
    }
  }

  const error =
    pickError ??
    (present.isError ? eventsErrorMessage(present.error) : null) ??
    (addPresenter.isError ? eventsErrorMessage(addPresenter.error) : null);

  return (
    <div
      role="dialog"
      aria-label="Add presenter"
      className="border-border bg-surface absolute top-9 left-0 z-20 w-64 rounded-[var(--radius-md)] border p-3 shadow-[var(--shadow-lg)] @max-md:right-0 @max-md:w-auto"
    >
      <div className="flex flex-col gap-2">
        {!alreadyPresenting && (
          <SelfSignUpRow
            isPending={present.isPending}
            onPress={handleSelfSignUp}
          />
        )}
        {canPickOthers && (
          <Roster
            candidateIds={roster}
            isPending={addPresenter.isPending}
            onPick={(id) => void handlePick(id)}
          />
        )}
      </div>
      {error && (
        <p role="alert" className="text-danger mt-2 text-xs">
          {error}
        </p>
      )}
    </div>
  );
}
