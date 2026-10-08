import {
  type QueryClient,
  useMutation,
  useMutationState,
  useQueryClient,
} from "@tanstack/react-query";

import { notifyError } from "@/components/ui/toast";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { declarationRemovalErrorMessage } from "@/features/expertise/declaration-errors";
import { learningKeys } from "@/features/learning/api/query-keys";

const gateway = httpExpertiseGateway;

interface DeclarationRef {
  slug: string;
  tag: string;
}

const REMOVE_KEY = [...expertiseKeys.all, "remove-declaration"] as const;

function sameDeclaration(a: DeclarationRef, b: unknown): boolean {
  const other = b as DeclarationRef;
  return a.slug === other.slug && a.tag === other.tag;
}

function refresh(queryClient: QueryClient, slug: string) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: expertiseKeys.selfView() }),
    queryClient.invalidateQueries({ queryKey: expertiseKeys.community(slug) }),
    // The Learning page lists who can be asked about each area.
    queryClient.invalidateQueries({ queryKey: learningKeys.community(slug) }),
  ]);
}

export function useDeclareArea(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (tag: string) => gateway.declare(slug, tag),
    onSuccess: () => refresh(queryClient, slug),
  });
}

/** Pending state is per declaration and shared by every control that removes it (a lane row and
 *  an area's marker), so a second press from either sends nothing. Checked against the mutation
 *  cache, which marks the first one pending at once. */
export function useRemoveDeclaration() {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationKey: REMOVE_KEY,
    mutationFn: ({ slug, tag }: DeclarationRef) =>
      gateway.removeDeclaration(slug, tag),
    onSuccess: (_data, { slug }) => refresh(queryClient, slug),
    onError: (error) => notifyError(declarationRemovalErrorMessage(error)),
  });
  const pending = useMutationState({
    filters: { mutationKey: REMOVE_KEY, status: "pending" },
    select: ({ state }) => state.variables,
  });
  function remove(declaration: DeclarationRef) {
    const inFlight = queryClient.isMutating({
      mutationKey: REMOVE_KEY,
      predicate: ({ state }) => sameDeclaration(declaration, state.variables),
    });
    if (inFlight === 0) mutation.mutate(declaration);
  }
  return {
    ...mutation,
    mutate: remove,
    isRemoving: (declaration: DeclarationRef) =>
      pending.some((variables) => sameDeclaration(declaration, variables)),
  };
}
