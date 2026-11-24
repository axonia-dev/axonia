import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { useIsDelegated } from "@/auth/delegated-session";
import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import { mentoringKeys } from "@/features/mentoring/api/query-keys";
import type { MentoringPage } from "@/features/mentoring/domain/mentoring";
import { retryUnlessRefused } from "@/features/mentoring/mentoring-errors";

const gateway = httpMentoringGateway;

export function useMentors(slug: string, page: number) {
  const delegated = useIsDelegated();
  return useQuery({
    queryKey: mentoringKeys.mentors(slug, page),
    queryFn: () => gateway.mentors(slug, page),
    enabled: !delegated,
    placeholderData: keepPreviousData,
    retry: retryUnlessRefused,
  });
}

/** The switch shows at once, before the server answers, and goes back if refused. */
export function useSetListing(slug: string) {
  const queryClient = useQueryClient();
  const pageKey = mentoringKeys.page(slug);
  return useMutation({
    mutationFn: (listed: boolean) => gateway.setListing(slug, listed),
    onMutate: async (listed: boolean) => {
      await queryClient.cancelQueries({ queryKey: pageKey });
      const before = queryClient.getQueryData<MentoringPage>(pageKey);
      if (before !== undefined) {
        queryClient.setQueryData<MentoringPage>(pageKey, {
          ...before,
          listing: listed ? "LISTED" : "OFF",
        });
      }
      return { before };
    },
    onError: (_error, _listed, context) => {
      if (context?.before !== undefined) {
        queryClient.setQueryData(pageKey, context.before);
      }
    },
    onSettled: () =>
      queryClient.invalidateQueries({
        queryKey: mentoringKeys.community(slug),
      }),
  });
}
