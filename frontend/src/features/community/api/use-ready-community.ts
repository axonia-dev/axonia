import { useQueryClient } from "@tanstack/react-query";
import { useMemo } from "react";

import { communityQuery } from "@/features/community/api/use-communities";

/** A morph into a community page from outside its shell waits for the shell's own read. */
export function useReadyCommunity() {
  const queryClient = useQueryClient();
  return useMemo(
    () => ({
      ready: async (slug: string) => {
        await queryClient.query({
          ...communityQuery(slug),
          staleTime: "static",
        });
      },
      prefetch: (slug: string) => {
        queryClient.query(communityQuery(slug)).catch(() => undefined);
      },
    }),
    [queryClient],
  );
}
