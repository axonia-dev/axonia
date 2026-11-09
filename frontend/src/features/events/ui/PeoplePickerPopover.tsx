import { useEffect, useState } from "react";

import { Button } from "@/components/ui/Button";
import { TextInput } from "@/components/ui/TextInput";
import {
  useMembers,
  usePeopleSearch,
} from "@/features/community/api/use-membership";
import { displayNameOf } from "@/features/events/ui/SlotPresenter";
import { useUsers } from "@/features/identity/api/use-users";
import { useDebouncedValue } from "@/lib/use-debounced-value";

export interface PeoplePickerPopoverProps {
  slug: string;
  /** The panel's accessible name, such as "Add presenter". */
  label: string;
  /** Left out of the roster and the search: already in. */
  excludeUserIds: string[];
  /** Hides the self row: the viewer is already in. */
  alreadyIn: boolean;
  /** Whoever may add someone other than themself. */
  canPickOthers: boolean;
  selfPending: boolean;
  otherPending: boolean;
  /** A refusal the caller's mutations report, shown under the panel. */
  error: string | null;
  /** Settles when the viewer is in; a rejection keeps the panel open. */
  onPickSelf: () => Promise<void>;
  /** Resolves false when the server could not match that person. */
  onPickOther: (userId: string) => Promise<boolean>;
  onClose: () => void;
}

const ROSTER_PARAMS = { page: 0, size: 100 } as const;

const NOT_MATCHED_MESSAGE = "That person could not be matched.";

const DEBOUNCE_MS = 300;

function SelfRow({
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
  excludeUserIds: string[];
  isPending: boolean;
  onPick: (userId: string) => void;
}

/** With nothing typed, the community's roster; once a name is typed, the server's search of the
 *  whole organization, members first, with everybody else marked. */
function useCandidates(
  slug: string,
  query: string,
  rosterIds: string[],
  excludeUserIds: string[],
): Candidate[] {
  const debounced = useDebouncedValue(query.trim(), DEBOUNCE_MS);
  const found = usePeopleSearch(slug, debounced).data ?? [];
  const directory = useUsers(rosterIds).data ?? {};
  if (query.trim() === "") {
    return rosterIds
      .filter((id) => !excludeUserIds.includes(id))
      .map((id) => ({
        userId: id,
        displayName: displayNameOf(id, directory),
        member: true,
      }));
  }
  return found.filter((person) => !excludeUserIds.includes(person.userId));
}

function Roster({
  slug,
  rosterIds,
  excludeUserIds,
  isPending,
  onPick,
}: Readonly<RosterProps>) {
  const [query, setQuery] = useState("");
  const visible = useCandidates(slug, query, rosterIds, excludeUserIds);

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
 * The panel opened by a people stack's "+": a pinned self row, and, for whoever may add
 * someone else, the community's roster, and a search of the whole organization once a name is
 * typed. It closes only once a pick lands, so a refusal stays readable.
 */
export function PeoplePickerPopover({
  slug,
  label,
  excludeUserIds,
  alreadyIn,
  canPickOthers,
  selfPending,
  otherPending,
  error,
  onPickSelf,
  onPickOther,
  onClose,
}: Readonly<PeoplePickerPopoverProps>) {
  const [pickError, setPickError] = useState<string | null>(null);
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

  function handlePickSelf() {
    void onPickSelf().then(onClose, () => undefined);
  }

  async function handlePickOther(userId: string) {
    setPickError(null);
    try {
      if (await onPickOther(userId)) {
        onClose();
        return;
      }
      setPickError(NOT_MATCHED_MESSAGE);
    } catch {
      // The caller's mutation already reports the refusal through `error`.
    }
  }

  const shown = pickError ?? error;

  return (
    <div
      role="dialog"
      aria-label={label}
      className="border-border bg-surface absolute top-9 left-0 z-20 w-64 rounded-[var(--radius-md)] border p-3 shadow-[var(--shadow-lg)] @max-md:right-0 @max-md:w-auto"
    >
      <div className="flex flex-col gap-2">
        {!alreadyIn && (
          <SelfRow isPending={selfPending} onPress={handlePickSelf} />
        )}
        {canPickOthers && (
          <Roster
            slug={slug}
            rosterIds={rosterIds}
            excludeUserIds={excludeUserIds}
            isPending={otherPending}
            onPick={(id) => void handlePickOther(id)}
          />
        )}
      </div>
      {shown && (
        <p role="alert" className="text-danger mt-2 text-xs">
          {shown}
        </p>
      )}
    </div>
  );
}
