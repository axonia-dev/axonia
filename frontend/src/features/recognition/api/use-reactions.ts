import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { httpReactionGateway } from "@/features/recognition/adapters/http-reaction-gateway";
import { recognitionKeys } from "@/features/recognition/api/query-keys";
import type { ReactionRef } from "@/features/recognition/domain/reaction";
import type { ContributionKey } from "@/features/recognition/domain/recognition";

const gateway = httpReactionGateway;

export function useReactions(key: ContributionKey) {
  return useQuery({
    queryKey: recognitionKeys.reactions(key),
    queryFn: () => gateway.tally(key),
  });
}

function useInvalidateReactions(key: ContributionKey) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({
      queryKey: recognitionKeys.reactions(key),
    });
  };
}

export function useReact(key: ContributionKey) {
  const invalidate = useInvalidateReactions(key);
  return useMutation({
    mutationFn: (ref: ReactionRef) => gateway.react(key, ref),
    onSuccess: invalidate,
  });
}

export function useUnreact(key: ContributionKey) {
  const invalidate = useInvalidateReactions(key);
  return useMutation({
    mutationFn: (ref: ReactionRef) => gateway.unreact(key, ref),
    onSuccess: invalidate,
  });
}

export function useReactionDefinitions() {
  return useQuery({
    queryKey: recognitionKeys.reactionDefinitions(),
    queryFn: () => gateway.listDefinitions(),
  });
}

/** `enabled` false so a caller who may not read the queue never asks for it: the vocabulary
 *  strip mounts for anybody holding any one of its catalogue permissions. */
export function useAllReactionDefinitions(enabled = true) {
  return useQuery({
    queryKey: recognitionKeys.allReactionDefinitions(),
    queryFn: () => gateway.listAllDefinitions(),
    enabled,
  });
}

/** How many proposals are waiting, for a caller that shows the number rather than the queue. */
export function usePendingReactionCount(enabled: boolean) {
  const definitions = useAllReactionDefinitions(enabled);
  return (definitions.data ?? []).filter((definition) => definition.pending)
    .length;
}

/** The caller's own waiting proposals, which is what a Withdraw needs something to sit beside. */
export function useMyReactionProposals() {
  return useQuery({
    queryKey: recognitionKeys.myReactionProposals(),
    queryFn: () => gateway.listMyProposals(),
  });
}

export function useReactionEligibility() {
  return useQuery({
    queryKey: recognitionKeys.reactionEligibility(),
    queryFn: () => gateway.eligibility(),
  });
}

function useInvalidateCatalog() {
  const queryClient = useQueryClient();
  return () => {
    // Removing an approved definition takes its placements down with it, so a bar still
    // holding a chip for it is drawing a reaction that no longer exists. The catalog and
    // the tallies move together or they disagree.
    void queryClient.invalidateQueries({
      queryKey: recognitionKeys.reactionsAll(),
    });
    // One prefix for all three definition reads: the picker's approved list, the
    // administration queue and the caller's own proposals all hang off it, and naming any of
    // them again here would only make an already-invalidated query refetch a second time.
    void queryClient.invalidateQueries({
      queryKey: recognitionKeys.reactionDefinitions(),
    });
    // Withdrawing frees a slot, so the cap that refused the last proposal has to stop being
    // true the moment it does - the count and the list move together or they disagree.
    void queryClient.invalidateQueries({
      queryKey: recognitionKeys.reactionEligibility(),
    });
  };
}

export function useProposeReaction() {
  const invalidate = useInvalidateCatalog();
  return useMutation({
    mutationFn: ({ name, file }: { name: string; file: File }) =>
      gateway.propose(name, file),
    onSuccess: invalidate,
  });
}

export function useApproveReaction() {
  const invalidate = useInvalidateCatalog();
  return useMutation({
    mutationFn: (id: number) => gateway.approve(id),
    onSuccess: invalidate,
  });
}

export function useRemoveReaction() {
  const invalidate = useInvalidateCatalog();
  return useMutation({
    mutationFn: (id: number) => gateway.remove(id),
    onSuccess: invalidate,
  });
}
