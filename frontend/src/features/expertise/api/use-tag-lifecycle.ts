import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { notify, notifyError } from "@/components/ui/toast";
import {
  scheduleKeys,
  sessionKeys,
  topicKeys,
} from "@/features/events/api/query-keys";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { curationErrorMessage } from "@/features/expertise/curation-errors";
import { artifactKeys } from "@/features/knowledge/api/query-keys";
import { learningKeys } from "@/features/learning/api/query-keys";

const gateway = httpExpertiseGateway;

/** What a rename or a delete would touch. Idle until a tag is actually picked. */
export function useTagUsage(slug: string, tag: string | null) {
  return useQuery({
    queryKey: [...expertiseKeys.curation(slug), "usage", tag],
    queryFn: () => gateway.tagUsage(slug, tag ?? ""),
    enabled: tag !== null,
  });
}

/**
 * Renaming and deleting a tag, the two curation verbs that rewrite real content.
 *
 * Merge, split and park each raise an undo toast because each is a reversible lens over the
 * same signals. These two are not: they rewrite the tag on every artifact, topic and talk slot
 * of the community. The confirmation dialog is the safety, and an undo affordance here would
 * promise a reversal that cannot be honoured. Success says what happened, and stops there.
 */
export function useTagLifecycle(slug: string) {
  const queryClient = useQueryClient();

  // A rename or a delete rewrites the tag on every artifact, topic, talk slot and area to
  // learn of the community, so the library's, the events' and the Learning page's caches go
  // as stale as the expertise ones. Sessions and the schedule are invalidated whole: a slot's tags are read through
  // keys pinned to a session id, not to the community's slug.
  const invalidate = () =>
    Promise.all([
      queryClient.invalidateQueries({
        queryKey: expertiseKeys.community(slug),
      }),
      queryClient.invalidateQueries({ queryKey: expertiseKeys.selfView() }),
      queryClient.invalidateQueries({ queryKey: artifactKeys.community(slug) }),
      queryClient.invalidateQueries({ queryKey: topicKeys.community(slug) }),
      queryClient.invalidateQueries({ queryKey: sessionKeys.all }),
      queryClient.invalidateQueries({ queryKey: scheduleKeys.all }),
      queryClient.invalidateQueries({ queryKey: learningKeys.community(slug) }),
    ]);

  const rename = useMutation({
    mutationFn: ({ from, to }: { from: string; to: string }) =>
      gateway.renameTag(slug, from, to),
    onSuccess: (_data, { from, to }) => {
      void invalidate();
      notify(`Renamed ${from} to ${to}`);
    },
    onError: (error) =>
      notifyError(curationErrorMessage(error, "Couldn't rename that tag.")),
  });

  const remove = useMutation({
    mutationFn: (tag: string) => gateway.deleteTag(slug, tag),
    onSuccess: (_data, tag) => {
      void invalidate();
      notify(`Deleted ${tag}`);
    },
    onError: (error) =>
      notifyError(curationErrorMessage(error, "Couldn't delete that tag.")),
  });

  return {
    renameTag: (from: string, to: string) => rename.mutate({ from, to }),
    deleteTag: (tag: string) => remove.mutate(tag),
    isPending: rename.isPending || remove.isPending,
  };
}
