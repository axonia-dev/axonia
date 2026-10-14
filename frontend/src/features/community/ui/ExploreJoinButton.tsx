import { useEffect } from "react";

import { Button } from "@/components/ui/Button";
import { useJoin } from "@/features/community/api/use-membership";
import { communityErrorMessage } from "@/features/community/community-errors";
import { preloadHeroCeremony } from "@/features/community/ui/ceremony/lazy-ceremonies";
import { useDepartForCeremony } from "@/features/community/ui/ceremony/use-depart-for-ceremony";

export function ExploreJoinButton({ slug }: Readonly<{ slug: string }>) {
  const depart = useDepartForCeremony();
  const join = useJoin(slug, () => depart("welcome", slug));

  useEffect(preloadHeroCeremony, []);

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        isPending={join.isPending}
        onPress={() => join.mutate()}
      >
        Join
      </Button>
      {join.isError && (
        <span
          role="alert"
          className="text-danger order-last basis-full pb-3 pl-4 text-sm"
        >
          {communityErrorMessage(join.error)}
        </span>
      )}
    </>
  );
}
