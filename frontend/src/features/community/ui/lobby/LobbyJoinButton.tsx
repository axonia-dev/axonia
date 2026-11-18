import { useEffect } from "react";

import { Button } from "@/components/ui/Button";
import { useJoin } from "@/features/community/api/use-membership";
import { communityErrorMessage } from "@/features/community/community-errors";
import { preloadHeroCeremony } from "@/features/community/ui/ceremony/lazy-ceremonies";
import { useLobbyDepart } from "@/features/community/ui/lobby/lobby-departure";
import { cn } from "@/lib/cn";

/** Join from the lobby: once the join lands, the page darkens and the community's Welcome plays. */
export function LobbyJoinButton({
  slug,
  size = "sm",
  onImage = false,
  fullWidth = false,
  className = "",
}: Readonly<{
  slug: string;
  size?: "sm" | "md";
  /** Drawn over a hero image, where the failure reads in white rather than in danger ink. */
  onImage?: boolean;
  fullWidth?: boolean;
  className?: string;
}>) {
  const depart = useLobbyDepart();
  const join = useJoin(slug, () => depart("welcome", slug));

  useEffect(preloadHeroCeremony, []);

  return (
    <span
      className={cn(
        "flex flex-col gap-2",
        fullWidth ? "w-full items-stretch" : "items-start",
      )}
    >
      <Button
        size={size}
        isPending={join.isPending}
        onPress={() => join.mutate()}
        className={className}
      >
        Join
      </Button>
      {join.isError && (
        <span
          role="alert"
          className={cn("text-xs", onImage ? "text-white" : "text-danger")}
        >
          {communityErrorMessage(join.error)}
        </span>
      )}
    </span>
  );
}
