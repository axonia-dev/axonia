import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/Button";
import { handOffCeremony } from "@/features/community/api/ceremony-handoff";
import { useJoin } from "@/features/community/api/use-membership";
import { communityErrorMessage } from "@/features/community/community-errors";
import { preloadHeroCeremony } from "@/features/community/ui/ceremony/lazy-ceremonies";

export function ExploreJoinButton({ slug }: Readonly<{ slug: string }>) {
  const navigate = useNavigate();
  const join = useJoin(slug, () => {
    handOffCeremony("welcome", slug);
    void navigate(`/communities/${slug}`);
  });

  useEffect(preloadHeroCeremony, []);

  return (
    <span className="flex items-center gap-2">
      {join.isError && (
        <span role="alert" className="text-danger text-sm">
          {communityErrorMessage(join.error)}
        </span>
      )}
      <Button
        variant="outline"
        size="sm"
        isDisabled={join.isPending}
        onPress={() => join.mutate()}
      >
        Join
      </Button>
    </span>
  );
}
