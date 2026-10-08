import { PresenterHoverCard } from "@/features/events/ui/PresenterHoverCard";
import { displayNameOf } from "@/features/events/ui/SlotPresenter";
import type { User } from "@/features/identity/domain/user";

interface Props {
  userIds: string[];
  users: Record<string, User>;
}

/** A compact, non-interactive read of who is presenting - this is a schedule-timeline entry,
 * not the slot card, so it carries none of the slot card's join/invite/manage affordances. */
export function PresenterAvatars({ userIds, users }: Readonly<Props>) {
  if (userIds.length === 0) {
    return null;
  }
  return (
    <span className="flex -space-x-1.5">
      {userIds.map((id) => (
        <PresenterHoverCard
          key={id}
          name={displayNameOf(id, users)}
          user={users[id]}
        />
      ))}
    </span>
  );
}
