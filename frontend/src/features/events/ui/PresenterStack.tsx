import { type ComponentProps } from "react";

import { currentUserId } from "@/auth/current-user";
import { useRemovePresenter } from "@/features/events/api/use-slot-presenters";
import { useWithdrawSlot } from "@/features/events/api/use-slot-sign-up";
import type { Slot } from "@/features/events/domain/slot";
import { AddPresenterPopover } from "@/features/events/ui/AddPresenterPopover";
import { peopleAction, PeopleStack } from "@/features/events/ui/PeopleStack";
import { PresenterHoverCard } from "@/features/events/ui/PresenterHoverCard";
import type { User } from "@/features/identity/domain/user";
import { useCreditTarget } from "@/features/recognition/ui/credit-targets";

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

function RegisteredPresenter({
  slotId,
  userId,
  ...card
}: Readonly<
  { slotId: number; userId: string } & ComponentProps<typeof PresenterHoverCard>
>) {
  const avatarRef = useCreditTarget(
    { contributionType: "SLOT_PRESENTED", contributionId: slotId },
    userId,
  );
  return <PresenterHoverCard {...card} avatarRef={avatarRef} />;
}

/**
 * A slot's presenters. Ownership lives here rather than on SlotActions: joining, inviting,
 * withdrawing, and removing are all presenter-membership changes, not slot-editing actions.
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
  const isPresenter = slot.presenterUserIds.includes(currentUserId() ?? "");
  const withdraw = useWithdrawSlot(slug, sessionId, sectionId, slot.id);
  const removePresenter = useRemovePresenter(slug, sessionId);

  return (
    <PeopleStack
      userIds={slot.presenterUserIds}
      users={users}
      noun="presenter"
      canManageOthers={canManageSchedule}
      interactive={interactive}
      renderFace={(userId, face) => (
        <RegisteredPresenter slotId={slot.id} userId={userId} {...face} />
      )}
      withdraw={peopleAction(withdraw, () => withdraw.mutate())}
      remove={peopleAction(removePresenter, (userId: string) =>
        removePresenter.mutate({ sectionId, slotId: slot.id, userId }),
      )}
      renderPicker={(onClose) => (
        <AddPresenterPopover
          slug={slug}
          sessionId={sessionId}
          sectionId={sectionId}
          slotId={slot.id}
          presenterUserIds={slot.presenterUserIds}
          alreadyPresenting={isPresenter}
          canPickOthers={canManageSchedule || isPresenter}
          onClose={onClose}
        />
      )}
    />
  );
}
