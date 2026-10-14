import {
  type QueryClient,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import { adminKeys } from "@/features/admin/api/query-keys";
import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { communityKeys } from "@/features/community/api/query-keys";
import type { MemberListParams } from "@/features/community/domain/community-gateway";
import type {
  CommunityRole,
  Membership,
} from "@/features/community/domain/membership";
import { expertiseKeys } from "@/features/expertise/api/query-keys";
import { learningKeys } from "@/features/learning/api/query-keys";
import type { Page } from "@/lib/page";

const gateway = httpCommunityGateway;

const ROSTER_PAGE_SIZE = 25;

/** The roster in display-name order, a page at a time. `display_name` is a server-known virtual
 *  sort key: names live in the identity directory, so the server orders the roster whole and
 *  slices it. */
export function useRosterByName(slug: string) {
  return useInfiniteQuery({
    queryKey: communityKeys.rosterByName(slug),
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      gateway.listMembers(slug, {
        page: pageParam,
        size: ROSTER_PAGE_SIZE,
        sort: "display_name",
      }),
    getNextPageParam: (last: Page<Membership>, pages) =>
      pages.length * ROSTER_PAGE_SIZE < last.totalElements
        ? pages.length
        : undefined,
  });
}

export function useMembers(slug: string, params: MemberListParams) {
  return useQuery({
    queryKey: communityKeys.members(slug, params),
    queryFn: () => gateway.listMembers(slug, params),
  });
}

/** Members matching typed text, for a picker. Blank text asks nothing. */
export function useMemberSearch(slug: string, text: string) {
  const trimmed = text.trim();
  return useQuery({
    queryKey: communityKeys.memberSearch(slug, trimmed),
    queryFn: () => gateway.searchMembers(slug, trimmed),
    enabled: trimmed !== "",
  });
}

// A refetch now would reshuffle the lobby under its departing curtain; the next mount refetches.
function markListsStale(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({
    queryKey: communityKeys.lists(),
    refetchType: "none",
  });
}

export function useMyMembership(slug: string) {
  return useQuery({
    queryKey: communityKeys.myMembership(slug),
    queryFn: () => gateway.myMembership(slug),
  });
}

/** `onJoined` fires the moment the join succeeds; the join stays pending until what it answers settles. */
export function useJoin(slug: string, onJoined?: () => void | Promise<void>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => gateway.join(slug),
    onSuccess: () => {
      return Promise.all([
        onJoined?.(),
        // detail(slug) is the prefix of myMembership(slug), so this also refreshes
        // the membership answer the hero and the management access both read.
        queryClient.invalidateQueries({ queryKey: communityKeys.detail(slug) }),
        // The Learning page reads whether the caller is a member.
        queryClient.invalidateQueries({
          queryKey: learningKeys.community(slug),
        }),
        markListsStale(queryClient),
      ]);
    },
  });
}

export function useLeave(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => gateway.leave(slug),
    onSuccess: () => {
      // Fire-and-forget: in a hub the caller's signinRedirect follows right after
      // onSuccess (leaving the hub's one community IS leaving the hub), and awaiting
      // refetches against the now-unbound org would only delay the redirect.
      // detail(slug) is the prefix of myMembership(slug), so this also refreshes
      // the membership answer the hero and the management access both read.
      void queryClient.invalidateQueries({
        queryKey: communityKeys.detail(slug),
      });
      void queryClient.invalidateQueries({
        queryKey: adminKeys.myOrganizations(),
      });
      // Leaving deletes the member's declarations and areas to learn in the community.
      void queryClient.invalidateQueries({
        queryKey: expertiseKeys.selfView(),
      });
      void queryClient.invalidateQueries({
        queryKey: learningKeys.community(slug),
      });
      void markListsStale(queryClient);
    },
  });
}

export function useChangeRole(slug: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: CommunityRole }) =>
      gateway.changeRole(slug, userId, role),
    onMutate: async ({ userId, role }) => {
      const queryKey = communityKeys.membersAll(slug);
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueriesData<Page<Membership>>({
        queryKey,
      });
      for (const [key, page] of previous) {
        if (page == null) continue;
        queryClient.setQueryData<Page<Membership>>(key, {
          ...page,
          content: page.content.map((m) =>
            m.userId === userId ? { ...m, role } : m,
          ),
        });
      }
      return { previous };
    },
    onError: (_error, _vars, context) => {
      for (const [key, page] of context?.previous ?? []) {
        queryClient.setQueryData(key, page);
      }
    },
    onSettled: () =>
      queryClient.invalidateQueries({
        queryKey: communityKeys.membersAll(slug),
      }),
  });
}
