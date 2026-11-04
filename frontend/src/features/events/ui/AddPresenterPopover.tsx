import { useEffect, useState } from "react";

import { Button } from "@/components/ui/Button";
import { TextInput } from "@/components/ui/TextInput";
import {
  useMembers,
  usePeopleSearch,
} from "@/features/community/api/use-membership";
import { useAddPresenter } from "@/features/events/api/use-slot-presenters";
import { usePresentSlot } from "@/features/events/api/use-slot-sign-up";
import { eventsErrorMessage } from "@/features/events/events-errors";
import { displayNameOf } from "@/features/events/ui/SlotPresenter";
import { useUsers } from "@/features/identity/api/use-users";
import { useDebouncedValue } from "@/lib/use-debounced-value";

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

const NOT_MATCHED_MESSAGE = "That person could not be matched.";

const DEBOUNCE_MS = 300;

function rosterCandidates(
  rosterIds: string[],
  presenterUserIds: string[],
): string[] {
  return rosterIds.filter((id) => !presenterUserIds.includes(id));
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

interface Candidate {
  userId: string;
  displayName: string;
  member: boolean;
}

interface RosterProps {
  slug: string;
  rosterIds: string[];
  presenterUserIds: string[];
  isPending: boolean;
  onPick: (userId: string) => void;
}

/** With nothing typed, the community's roster; once a name is typed, the server's search of the
 *  whole organization, members first, with everybody else marked. */
function useCandidates(
  slug: string,
  query: string,
  rosterIds: string[],
  presenterUserIds: string[],
): Candidate[] {
  const debounced = useDebouncedValue(query.trim(), DEBOUNCE_MS);
  const found = usePeopleSearch(slug, debounced).data ?? [];
  const directory = useUsers(rosterIds).data ?? {};
  if (query.trim() === "") {
    return rosterCandidates(rosterIds, presenterUserIds).map((id) => ({
      userId: id,
      displayName: displayNameOf(id, directory),
      member: true,
    }));
  }
  return found.filter((person) => !presenterUserIds.includes(person.userId));
}

function Roster({
  slug,
  rosterIds,
  presenterUserIds,
  isPending,
  onPick,
}: Readonly<RosterProps>) {
  const [query, setQuery] = useState("");
  const visible = useCandidates(slug, query, rosterIds, presenterUserIds);

  return (
    <div className="flex flex-col gap-2">
      <TextInput
        label="Search people"
        value={query}
        onChange={setQuery}
        placeholder="Search people"
      />
      <ul className="relative flex max-h-48 flex-col gap-0.5 overflow-y-auto">
        {visible.map((person) => (
          <li key={person.userId}>
            <button
              type="button"
              disabled={isPending}
              onClick={() => onPick(person.userId)}
              className="hover:bg-surface-sunken text-foreground flex w-full cursor-pointer items-center justify-between gap-2 rounded px-2 py-1.5 text-left text-sm disabled:cursor-not-allowed disabled:opacity-50"
            >
              <span className="min-w-0 truncate">{person.displayName}</span>
              {!person.member && (
                <span className="text-muted-foreground shrink-0 text-xs">
                  Not a member
                </span>
              )}
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
 * slot), the community's roster, and a search of the whole organization once a name is typed.
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
  const rosterIds = (members.data?.content ?? []).map(
    (member) => member.userId,
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
            slug={slug}
            rosterIds={rosterIds}
            presenterUserIds={presenterUserIds}
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
