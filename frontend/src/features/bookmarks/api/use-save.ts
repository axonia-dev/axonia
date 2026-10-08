import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { notify } from "@/components/ui/toast";
import { httpSaveGateway } from "@/features/bookmarks/adapters/http-save-gateway";
import { saveKeys } from "@/features/bookmarks/api/query-keys";
import type { SaveableRef } from "@/features/bookmarks/domain/saved";

const gateway = httpSaveGateway;

function useInvalidate(ref: SaveableRef) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: saveKeys.state(ref) });
    void queryClient.invalidateQueries({ queryKey: saveKeys.mine() });
  };
}

export function useSaveState(ref: SaveableRef) {
  return useQuery({
    queryKey: saveKeys.state(ref),
    queryFn: () => gateway.getState(ref),
  });
}

export function useSave(ref: SaveableRef, onView?: () => void) {
  const invalidate = useInvalidate(ref);
  return useMutation({
    mutationFn: () => gateway.save(ref),
    onSuccess: () => {
      notify("Saved", onView ? { label: "View", onPress: onView } : undefined);
      invalidate();
    },
  });
}

export function useUnsave(ref: SaveableRef) {
  const invalidate = useInvalidate(ref);
  return useMutation({
    mutationFn: () => gateway.unsave(ref),
    onSuccess: () => {
      notify("Removed", {
        label: "Undo",
        onPress: () => {
          void gateway.save(ref).then(invalidate);
        },
      });
      invalidate();
    },
  });
}

export function useMarkHelpful(artifactId: number) {
  const invalidate = useInvalidate({ kind: "artifact", id: artifactId });
  return useMutation({
    mutationFn: (helped: boolean | null) =>
      gateway.markHelpful(artifactId, helped),
    onSuccess: invalidate,
  });
}

export function useMySaved() {
  return useQuery({
    queryKey: saveKeys.mine(),
    queryFn: () => gateway.listMine(),
  });
}
