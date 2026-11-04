import { useCallback, useState } from "react";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { Button } from "@/components/ui/Button";
import { ComboBox, type ComboBoxOption } from "@/components/ui/ComboBox";
import {
  usePeopleSearch,
  useRosterByName,
} from "@/features/community/api/use-membership";
import type {
  CreditablePerson,
  Membership,
} from "@/features/community/domain/membership";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";
import { useDebouncedValue } from "@/lib/use-debounced-value";
import { userHue } from "@/lib/user-hue";

export interface PickedMember {
  userId: string;
  displayName: string;
}

interface Props {
  slug: string;
  label: string;
  picked: PickedMember | null;
  placeholder?: string;
  onPick: (member: PickedMember) => void;
  onClear?: () => void;
}

const DEBOUNCE_MS = 300;
const NOT_A_MEMBER = "Not a member";

function faceFor(userId: string, name: string, user: User | undefined) {
  return (
    <MemberAvatar
      name={name}
      hue={userHue(user?.id ?? userId)}
      subject={userId}
      src={user?.avatarUrl ?? null}
      className="size-6 text-[10px]"
    />
  );
}

/** The text searched on follows the field: typing, the revert on blur, and a pick cleared or
 *  replaced from outside. A pick keeps the search that found it. */
function useSearchText(shown: string) {
  const [text, setText] = useState("");
  const [lastShown, setLastShown] = useState(shown);
  const [pickedLabel, setPickedLabel] = useState<string | null>(null);
  if (shown !== lastShown) {
    setLastShown(shown);
    if (shown !== pickedLabel) {
      setText("");
    }
  }
  return { text, setText, notePick: setPickedLabel };
}

function statusFor(
  query: string,
  debouncing: boolean,
  isFetching: boolean,
  resultCount: number,
): string | undefined {
  if (query === "") return undefined;
  if (debouncing || isFetching) return "Searching";
  return resultCount === 0 ? "No one found" : undefined;
}

function rosterContent(
  data: { pages: { content: Membership[] }[] } | undefined,
): Membership[] {
  return data?.pages.flatMap((page) => page.content) ?? [];
}

/** A roster row names only a user id; the display name is the identity directory's to give, so
 *  the id stands in as the label until `useUsers` answers. */
function rosterAsMembers(rosterMembers: Membership[]): CreditablePerson[] {
  return rosterMembers.map((m) => ({
    userId: m.userId,
    displayName: m.userId,
    member: true,
  }));
}

function optionsFor(
  people: CreditablePerson[],
  directory: Record<string, User>,
): ComboBoxOption[] {
  return people.map((person) => {
    const name = directory[person.userId]?.displayName ?? person.displayName;
    return {
      id: person.userId,
      label: name,
      icon: faceFor(person.userId, name, directory[person.userId]),
      ...(person.member ? {} : { hint: NOT_A_MEMBER }),
    };
  });
}

function comboBoxExtras(
  status: string | undefined,
  placeholder: string | undefined,
) {
  return {
    ...(status === undefined ? {} : { statusMessage: status }),
    ...(placeholder === undefined ? {} : { placeholder }),
  };
}

/**
 * A person in the organization, named precisely. One field for every form that credits a person, so
 * the assignee, the presenters and the contributor all read the same way.
 *
 * Opens on the community's roster in display-name order and pages as the list is scrolled; typing
 * hands over to the server's own name search, which reaches the whole organization, lists members
 * first and marks everybody else. Picking somebody credits them without making them a member. The
 * value exchanged is always a userId, never free text.
 */
export function MemberSelectField({
  slug,
  label,
  picked,
  placeholder,
  onPick,
  onClear,
}: Readonly<Props>) {
  const shown = picked?.displayName ?? "";
  const searchText = useSearchText(shown);
  const query = searchText.text.trim();
  const debouncedQuery = useDebouncedValue(query, DEBOUNCE_MS);
  const roster = useRosterByName(slug);
  const fetchNextPage = roster.fetchNextPage;
  const loadNextPage = useCallback(() => {
    void fetchNextPage();
  }, [fetchNextPage]);
  const search = usePeopleSearch(slug, debouncedQuery);

  const searching = query !== "";
  const rosterMembers = rosterContent(roster.data);
  const members = searching
    ? (search.data ?? [])
    : rosterAsMembers(rosterMembers);
  const directory = useUsers(members.map((m) => m.userId)).data ?? {};
  const options = optionsFor(members, directory);

  const status = statusFor(
    query,
    query !== debouncedQuery,
    search.isFetching,
    options.length,
  );

  return (
    <div className="flex items-end gap-2">
      <div className="min-w-0 flex-1">
        <ComboBox
          label={label}
          value={shown}
          options={options}
          serverFiltered
          hasMore={!searching && roster.hasNextPage === true}
          isLoadingMore={roster.isFetchingNextPage}
          onLoadMore={loadNextPage}
          {...comboBoxExtras(status, placeholder)}
          onInputChange={searchText.setText}
          onSelect={(option) => {
            searchText.notePick(option.label);
            onPick({ userId: option.id, displayName: option.label });
          }}
        />
      </div>
      {picked !== null && onClear !== undefined && (
        // A transparent border gives the button the field's height, so the bottom edges align.
        <Button
          type="button"
          variant="ghost"
          className="border border-transparent"
          onPress={onClear}
        >
          Clear
        </Button>
      )}
    </div>
  );
}
