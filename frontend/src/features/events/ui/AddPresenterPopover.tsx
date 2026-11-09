import { useAddPresenter } from "@/features/events/api/use-slot-presenters";
import { usePresentSlot } from "@/features/events/api/use-slot-sign-up";
import { eventsErrorMessage } from "@/features/events/events-errors";
import { PeoplePickerPopover } from "@/features/events/ui/PeoplePickerPopover";

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

/** The slot's people picker: signing up presents the slot, a pick names a co-presenter. */
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
  const present = usePresentSlot(slug, sessionId, sectionId, slotId);
  const addPresenter = useAddPresenter(slug, sessionId);

  function presentSelf() {
    return new Promise<void>((resolve, reject) => {
      present.mutate(undefined, {
        onSuccess: () => resolve(),
        onError: reject,
      });
    });
  }

  async function pickOther(userId: string) {
    const result = await addPresenter.mutateAsync({
      sectionId,
      slotId,
      presenter: userId,
    });
    return result.assigned;
  }

  const error =
    (present.isError ? eventsErrorMessage(present.error) : null) ??
    (addPresenter.isError ? eventsErrorMessage(addPresenter.error) : null);

  return (
    <PeoplePickerPopover
      slug={slug}
      label="Add presenter"
      excludeUserIds={presenterUserIds}
      alreadyIn={alreadyPresenting}
      canPickOthers={canPickOthers}
      selfPending={present.isPending}
      otherPending={addPresenter.isPending}
      error={error}
      onPickSelf={presentSelf}
      onPickOther={pickOther}
      onClose={onClose}
    />
  );
}
