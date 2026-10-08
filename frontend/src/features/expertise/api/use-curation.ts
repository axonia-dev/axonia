import { useQuery, useQueryClient } from "@tanstack/react-query";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { learningKeys } from "@/features/learning/api/query-keys";

const gateway = httpExpertiseGateway;

/** Merging, splitting and parking change which canonical area a name reads as, and areas to
 *  learn are read through the same vocabulary. */
export function useInvalidateCommunityAndSelf(slug: string) {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({
        queryKey: expertiseKeys.community(slug),
      }),
      queryClient.invalidateQueries({ queryKey: expertiseKeys.selfView() }),
      queryClient.invalidateQueries({ queryKey: learningKeys.community(slug) }),
    ]);
}

export function useCurationSuggestions(slug: string) {
  return useQuery({
    queryKey: expertiseKeys.curation(slug),
    queryFn: () => gateway.curationSuggestions(slug),
  });
}

/** The names a curator can act on, which is wider than the names a search lists. */
export function useCurationVocabulary(slug: string) {
  return useQuery({
    queryKey: expertiseKeys.curationVocabulary(slug),
    queryFn: () => gateway.curationVocabulary(slug),
  });
}
