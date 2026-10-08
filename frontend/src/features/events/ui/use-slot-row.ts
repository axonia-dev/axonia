import { useState } from "react";

import { currentUserId } from "@/auth/current-user";
import { useRemoveSlot } from "@/features/events/api/use-slot-sign-up";
import type { Slot } from "@/features/events/domain/slot";
import { isOpen } from "@/features/events/domain/slot";
import { eventsErrorMessage } from "@/features/events/events-errors";

/**
 * Encapsulates the mutation state for a SlotRow that is still the row's own concern: removing
 * the slot itself, and the edit/remove dialog toggles. Presenter membership (sign up, invite,
 * withdraw, remove a presenter) is owned by PresenterStack, not here.
 */
export function useSlotRow(
  slug: string,
  sessionId: number,
  sectionId: number,
  slot: Slot,
) {
  const remove = useRemoveSlot(slug, sessionId, sectionId, slot.id);
  const [editing, setEditing] = useState(false);
  const [removing, setRemoving] = useState(false);
  const [leaving, setLeaving] = useState(false);

  const errorMessage = remove.error ? eventsErrorMessage(remove.error) : null;

  const openEditing = () => setEditing(true);

  const beginRemove = () => {
    setLeaving(true);
    remove.mutate(undefined, { onError: () => setLeaving(false) });
  };

  return {
    remove,
    editing,
    setEditing,
    removing,
    setRemoving,
    leaving,
    errorMessage,
    isPresenter: slot.presenterUserIds.includes(currentUserId() ?? ""),
    open: isOpen(slot),
    openEditing,
    beginRemove,
  };
}
