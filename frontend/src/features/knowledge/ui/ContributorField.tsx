import { useState } from "react";

import { currentUserId } from "@/auth/current-user";
import {
  MemberSelectField,
  type PickedMember,
} from "@/components/patterns/MemberSelectField";
import { Select } from "@/components/ui/Select";
import { useUsers } from "@/features/identity/api/use-users";

interface PresenterChoiceProps {
  presenterUserIds: string[];
  value: string | null;
  onChange: (userId: string) => void;
}

/** Credit on a talk: a fixed choice among the slot's presenters plus the caller, not a roster
 *  search - a slot has at most a few presenters. */
function PresenterChoice({
  presenterUserIds,
  value,
  onChange,
}: Readonly<PresenterChoiceProps>) {
  const me = currentUserId();
  // A Map, not a Record: eslint reads a Record's bracket lookup as object injection.
  const directory = new Map(
    Object.entries(useUsers(presenterUserIds).data ?? {}),
  );
  const nameOf = (userId: string) =>
    directory.get(userId)?.displayName ?? "this slot's presenter";

  const [sole] = presenterUserIds;
  if (sole !== undefined && presenterUserIds.length === 1) {
    return (
      <p className="text-muted-foreground text-sm">
        Credited to {nameOf(sole)}, who presented this slot.
      </p>
    );
  }

  const options = [
    ...presenterUserIds
      .filter((userId) => userId !== me)
      .map((userId) => ({ id: userId, label: nameOf(userId) })),
    ...(me === undefined ? [] : [{ id: me, label: "Me" }]),
  ];
  return (
    <Select
      label="Contributor"
      value={value}
      options={options}
      placeholder="Choose who this is by"
      onChange={onChange}
    />
  );
}

interface Credit {
  canCredit: boolean;
  /** Whose contribution this is, or null for the caller. */
  contributor: string | null;
  choosePresenter: (userId: string) => void;
  picked: PickedMember | null;
  pick: (member: PickedMember) => void;
  clear: () => void;
  /** Two or more speakers and nobody named yet: there is no safe default, so the form waits. */
  mustChoose: boolean;
  /** The request fragment: the field, or nothing at all when the contributor is the caller. */
  credited: { contributor?: string };
}

function creditedUserId(
  presenters: string[],
  chosenPresenter: string | null,
  picked: PickedMember | null,
): string | null {
  if (presenters.length === 1) {
    return presenters[0] ?? null;
  }
  if (presenters.length > 1) {
    return chosenPresenter;
  }
  return picked === null ? null : picked.userId;
}

/** Whose contribution the new artifact is: nobody but the caller unless the community lets the
 *  caller credit contributions; then the sole presenter with no question asked, a choice required
 *  among several, or any member picked (the caller by default) when there is none. */
export function useCredit(
  presenterUserIds: string[],
  canCredit: boolean,
): Credit {
  const [chosenPresenter, setChosenPresenter] = useState<string | null>(null);
  const [picked, setPicked] = useState<PickedMember | null>(null);
  const presenters = canCredit ? presenterUserIds : [];
  const contributor = canCredit
    ? creditedUserId(presenters, chosenPresenter, picked)
    : null;
  const mustChoose = presenters.length > 1 && contributor === null;
  // Omitted is the one value the backend accepts from anybody, so "Me" can never turn into a 403.
  const credited =
    contributor !== null && contributor !== currentUserId()
      ? { contributor }
      : {};
  return {
    canCredit,
    contributor,
    choosePresenter: setChosenPresenter,
    picked,
    pick: setPicked,
    clear: () => setPicked(null),
    mustChoose,
    credited,
  };
}

interface Props {
  slug: string;
  presenterUserIds: string[];
  credit: Credit;
}

export function ContributorField({
  slug,
  presenterUserIds,
  credit,
}: Readonly<Props>) {
  if (!credit.canCredit) {
    return null;
  }
  if (presenterUserIds.length > 0) {
    return (
      <PresenterChoice
        presenterUserIds={presenterUserIds}
        value={credit.contributor}
        onChange={credit.choosePresenter}
      />
    );
  }
  return (
    <MemberSelectField
      slug={slug}
      label="Contributor"
      placeholder="Me"
      picked={credit.picked}
      onPick={credit.pick}
      onClear={credit.clear}
    />
  );
}
