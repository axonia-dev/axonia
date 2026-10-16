import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";

import { communityKeys } from "@/features/community/api/query-keys";
import {
  type CommunityListing,
  communitySchema,
} from "@/features/community/domain/community";

export function useSeedCommunityDetail(): (
  listing: CommunityListing,
) => Promise<void> {
  const queryClient = useQueryClient();
  return useCallback(
    async (listing: CommunityListing) => {
      const key = communityKeys.detail(listing.slug);
      if (queryClient.getQueryData(key) !== undefined) return;
      queryClient.setQueryData(key, communitySchema.parse(listing), {
        updatedAt: 0,
      });
    },
    [queryClient],
  );
}
