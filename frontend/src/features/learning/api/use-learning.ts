import {
  skipToken,
  useMutation,
  useMutationState,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { notifyError } from "@/components/ui/toast";
import { httpLearningGateway } from "@/features/learning/adapters/http-learning-gateway";
import { learningKeys } from "@/features/learning/api/query-keys";
import { learningRemovalErrorMessage } from "@/features/learning/learning-errors";

const gateway = httpLearningGateway;

export function useLearningPage(slug: string) {
  return useQuery({
    queryKey: learningKeys.page(slug),
    queryFn: () => gateway.page(slug),
  });
}

/** Idle until an area is picked; no placeholder data, so a cleared pick shows nothing stale. */
export function useLearners(slug: string, tag: string | null) {
  return useQuery({
    queryKey: learningKeys.learners(slug, tag ?? ""),
    queryFn: tag === null ? skipToken : () => gateway.learners(slug, tag),
  });
}

function useRefresh(slug: string) {
  const queryClient = useQueryClient();
  return () =>
    queryClient.invalidateQueries({ queryKey: learningKeys.community(slug) });
}

export function useAddIntent(slug: string) {
  const refresh = useRefresh(slug);
  return useMutation({
    mutationFn: (tag: string) => gateway.add(slug, tag),
    onSuccess: refresh,
  });
}

/** Pending state is per area: a mutation tracks only its latest call, so each area in flight is read
 *  from the mutation cache, which marks it pending at once, and a second press of it sends nothing. */
export function useRemoveIntent(slug: string) {
  const queryClient = useQueryClient();
  const refresh = useRefresh(slug);
  const mutationKey = learningKeys.removal(slug);
  const mutation = useMutation({
    mutationKey,
    mutationFn: (tag: string) => gateway.remove(slug, tag),
    onSuccess: refresh,
    onError: (error) => notifyError(learningRemovalErrorMessage(error)),
  });
  const pending = useMutationState({
    filters: { mutationKey, status: "pending" },
    select: ({ state }) => state.variables,
  });
  function remove(tag: string) {
    const inFlight = queryClient.isMutating({
      mutationKey,
      predicate: ({ state }) => state.variables === tag,
    });
    if (inFlight === 0) mutation.mutate(tag);
  }
  return {
    ...mutation,
    mutate: remove,
    isRemoving: (tag: string) => pending.includes(tag),
  };
}
