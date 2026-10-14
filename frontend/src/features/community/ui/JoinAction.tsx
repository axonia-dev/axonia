import { useEffect, useRef } from "react";

import { Button } from "@/components/ui/Button";
import { useJoin } from "@/features/community/api/use-membership";
import { communityErrorMessage } from "@/features/community/community-errors";
import { preloadHeroCeremony } from "@/features/community/ui/ceremony/lazy-ceremonies";

interface JoinActionProps {
  slug: string;
  onJoined?: () => void;
}

/** The community's front door: the one call to action a visitor is offered. */
export function JoinAction({ slug, onJoined }: Readonly<JoinActionProps>) {
  const pressed = useRef<(() => void) | undefined>(undefined);
  const join = useJoin(slug, () => pressed.current?.());

  useEffect(preloadHeroCeremony, []);

  // The callback is pinned at press time: a render during the request must not swap it, or the
  // ceremony would count from a number the join has already changed.
  const onPress = () => {
    pressed.current = onJoined;
    join.mutate();
  };

  return (
    <div className="flex flex-col gap-1">
      <Button isPending={join.isPending} onPress={onPress}>
        Join
      </Button>
      {join.isError && (
        <p role="alert" className="text-danger text-sm">
          {communityErrorMessage(join.error)}
        </p>
      )}
    </div>
  );
}
