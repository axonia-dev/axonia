import { useMutation } from "@tanstack/react-query";

import { notify, notifyError } from "@/components/ui/toast";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { useInvalidateCommunityAndSelf } from "@/features/expertise/api/use-curation";

const gateway = httpExpertiseGateway;

function mergedMessage(variants: string[], canonical: string): string {
  return `Merged ${variants.join(", ")} → ${canonical}`;
}

export function useTagMerge(slug: string) {
  const invalidate = useInvalidateCommunityAndSelf(slug);

  const undo = useMutation({
    mutationFn: (variants: string[]) =>
      Promise.all(variants.map((v) => gateway.split(slug, v))).then(
        () => undefined,
      ),
    onSuccess: invalidate,
  });

  const merge = useMutation({
    mutationFn: ({
      variants,
      canonical,
    }: {
      variants: string[];
      canonical: string;
    }) => gateway.mergeBatch(slug, variants, canonical),
    onSuccess: (_data, { variants, canonical }) => {
      void invalidate();
      notify(mergedMessage(variants, canonical), {
        label: variants.length > 1 ? "Undo all" : "Undo",
        onPress: () => undo.mutate(variants),
      });
    },
    onError: () => notifyError("Couldn't merge those tags."),
  });

  // Undoing a split re-merges the variant back into the canonical it left.
  const undoSplit = useMutation({
    mutationFn: ({
      variant,
      canonical,
    }: {
      variant: string;
      canonical: string;
    }) => gateway.mergeBatch(slug, [variant], canonical),
    onSuccess: invalidate,
  });

  const split = useMutation({
    mutationFn: ({ variant }: { variant: string; canonical: string }) =>
      gateway.split(slug, variant),
    onSuccess: (_data, { variant, canonical }) => {
      void invalidate();
      notify(`Split ${variant} out of ${canonical}`, {
        label: "Undo",
        onPress: () => undoSplit.mutate({ variant, canonical }),
      });
    },
    onError: () => notifyError("Couldn't split that tag."),
  });

  return {
    mergeInto: (variants: string[], canonical: string) =>
      merge.mutate({ variants, canonical }),
    splitOff: (variant: string, canonical: string) =>
      split.mutate({ variant, canonical }),
  };
}
