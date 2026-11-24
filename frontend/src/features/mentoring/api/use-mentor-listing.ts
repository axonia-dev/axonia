import { useQuery } from "@tanstack/react-query";

import { useIsDelegated } from "@/auth/delegated-session";
import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import { mentoringKeys } from "@/features/mentoring/api/query-keys";
import { useRefreshing } from "@/features/mentoring/api/use-refreshing";
import { retryUnlessRefused } from "@/features/mentoring/mentoring-errors";

const gateway = httpMentoringGateway;

export function useMentors(slug: string, page: number) {
  const delegated = useIsDelegated();
  return useQuery({
    queryKey: mentoringKeys.mentors(slug, page),
    queryFn: () => gateway.mentors(slug, page),
    enabled: !delegated,
    retry: retryUnlessRefused,
  });
}

/** Listed or not changes who the page names and what See all holds, so both are read again. */
export function useSetListing(slug: string) {
  return useRefreshing(
    (listed: boolean) => gateway.setListing(slug, listed),
    () => [mentoringKeys.community(slug)],
  );
}
