import { useParams } from "react-router-dom";

import { useCommunity } from "@/features/community/api/use-communities";
import type { Community } from "@/features/community/domain/community";

export interface PageCommunity {
  slug: string | undefined;
  scoped: boolean;
  community: Community | undefined;
  isLoading: boolean;
}

/** The community a member's own page sits inside, when its route carries one. */
export function usePageCommunity(): PageCommunity {
  const { slug } = useParams();
  const community = useCommunity(slug);
  const scoped = slug !== undefined;
  return {
    slug,
    scoped,
    community: community.data,
    isLoading: scoped && community.isLoading,
  };
}
