import { useMutation, useQueryClient } from "@tanstack/react-query";

import { notify, notifyError } from "@/components/ui/toast";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import type { DismissInput } from "@/features/expertise/domain/expertise-gateway";

const gateway = httpExpertiseGateway;

export function useSetOptOut() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (optedOut: boolean) => gateway.setOptOut(optedOut),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: expertiseKeys.all }),
  });
}

export function useHideTag() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (tag: string) => gateway.hideTag(tag),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: expertiseKeys.all }),
  });
}

const UNHIDE_KEY = ["expertise", "unhide"] as const;

/** A second press for a tag already being shown sends nothing. Checked against the mutation cache,
 *  which marks the first one pending at once, where `isPending` reaches the component a tick later. */
export function useUnhideTag() {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationKey: UNHIDE_KEY,
    mutationFn: (tag: string) => gateway.unhideTag(tag),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: expertiseKeys.all }),
    onError: () => notifyError("Could not show that area."),
  });
  function unhide(tag: string) {
    const inFlight = queryClient.isMutating({
      mutationKey: UNHIDE_KEY,
      predicate: ({ state }) => state.variables === tag,
    });
    if (inFlight === 0) mutation.mutate(tag);
  }
  return { ...mutation, mutate: unhide };
}

export function useDismissSignal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: DismissInput) => gateway.dismiss(input),
    onSuccess: () => {
      notify("Signal dismissed");
      return Promise.all([
        queryClient.invalidateQueries({ queryKey: expertiseKeys.selfView() }),
        queryClient.invalidateQueries({ queryKey: expertiseKeys.all }),
      ]);
    },
    onError: () => notifyError("Couldn't dismiss that signal."),
  });
}
