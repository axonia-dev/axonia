import { useMutation } from "@tanstack/react-query";

import { notify, notifyError } from "@/components/ui/toast";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { useInvalidateCommunityAndSelf } from "@/features/expertise/api/use-curation";

const gateway = httpExpertiseGateway;

export function useTagDismissal(slug: string) {
  const invalidate = useInvalidateCommunityAndSelf(slug);

  const restore = useMutation({
    mutationFn: ({ tagA, tagB }: { tagA: string; tagB: string }) =>
      gateway.restoreSuggestion(slug, tagA, tagB),
    onSuccess: invalidate,
    onError: () => notifyError("Couldn't restore that suggestion."),
  });

  const dismiss = useMutation({
    mutationFn: ({ tagA, tagB }: { tagA: string; tagB: string }) =>
      gateway.dismissSuggestion(slug, tagA, tagB),
    onSuccess: (_data, { tagA, tagB }) => {
      void invalidate();
      notify(`Dismissed ${tagA} / ${tagB}`, {
        label: "Undo",
        onPress: () => restore.mutate({ tagA, tagB }),
      });
    },
    onError: () => notifyError("Couldn't dismiss that suggestion."),
  });

  return {
    dismissPair: (tagA: string, tagB: string) => dismiss.mutate({ tagA, tagB }),
  };
}
