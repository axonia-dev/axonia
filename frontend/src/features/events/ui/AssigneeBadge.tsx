import { UserRound } from "lucide-react";

import { PresenterHoverCard } from "@/features/events/ui/PresenterHoverCard";
import type { User } from "@/features/identity/domain/user";

interface Props {
  name: string | null;
  user?: User | undefined;
}

/** Who owes the item, as a face. Unassigned is a ghost rather than a coloured disc, because a
 *  monogram hue for the word "Unassigned" reads as a person who is not there. */
export function AssigneeBadge({ name, user }: Readonly<Props>) {
  if (name === null) {
    return (
      <span
        aria-label="Unassigned"
        role="img"
        className="border-border-strong text-muted-foreground flex size-7 items-center justify-center rounded-full border border-dashed"
      >
        <UserRound className="size-3.5" aria-hidden />
      </span>
    );
  }
  return <PresenterHoverCard name={name} user={user} />;
}
