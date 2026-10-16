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
    (listing: CommunityListing) =>
      new Promise<void>((resolve) => {
        const key = communityKeys.detail(listing.slug);
        if (queryClient.getQueryData(key) === undefined) {
          queryClient.setQueryData(key, communitySchema.parse(listing), {
            updatedAt: 0,
          });
        }
        resolve();
      }),
    [queryClient],
  );
}
