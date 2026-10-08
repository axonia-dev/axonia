import { useInfiniteQuery } from "@tanstack/react-query";

import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { expertiseKeys } from "@/features/expertise/api/query-keys";

const gateway = httpExpertiseGateway;
const PAGE = 20;

function evidencePage(tag: string, offset: number, slug: string | undefined) {
  return slug === undefined
    ? gateway.areaEvidence(tag, offset, PAGE)
    : gateway.areaEvidenceIn(slug, tag, offset, PAGE);
}

/** Pages an area's full evidence, newest-first, starting just past the `recent` window already shown.
 *  With a slug, only the evidence recorded in that community. */
export function useAreaEvidence(
  tag: string,
  startOffset: number,
  enabled: boolean,
  slug?: string,
) {
  return useInfiniteQuery({
    queryKey: expertiseKeys.areaEvidence(tag, slug),
    enabled,
    initialPageParam: startOffset,
    queryFn: ({ pageParam }) => evidencePage(tag, pageParam, slug),
    getNextPageParam: (last, _all, lastParam) =>
      last.hasMore ? lastParam + PAGE : undefined,
  });
}
