import { Loader2 } from "lucide-react";
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
    <>
      <Button
        variant="outline"
        size="sm"
        className="relative"
        isDisabled={join.isPending}
        onPress={() => join.mutate()}
      >
        <span className={join.isPending ? "opacity-0" : undefined}>Join</span>
        {join.isPending && (
          <Loader2
            data-join-spinner
            size={14}
            aria-hidden
            className="absolute motion-safe:animate-spin"
          />
        )}
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
