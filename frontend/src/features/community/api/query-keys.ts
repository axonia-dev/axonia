import type { Query } from "@tanstack/react-query";

import type {
  ListParams,
  MemberListParams,
} from "@/features/community/domain/community-gateway";

export const communityKeys = {
  all: ["communities"] as const,
  lists: () => [...communityKeys.all, "list"] as const,
  list: (params: ListParams) => [...communityKeys.lists(), params] as const,
  detail: (slug: string) => [...communityKeys.all, "detail", slug] as const,
  membersAll: (slug: string) =>
    [...communityKeys.detail(slug), "members"] as const,
  members: (slug: string, params: MemberListParams) =>
    [...communityKeys.membersAll(slug), params] as const,
  memberSearch: (slug: string, text: string) =>
    [...communityKeys.membersAll(slug), "search", text] as const,
  rosterByName: (slug: string) =>
    [...communityKeys.detail(slug), "roster-by-name"] as const,
  myMembership: (slug: string) =>
    [...communityKeys.detail(slug), "membership", "me"] as const,
  contributionPolicy: (slug: string) =>
    [...communityKeys.detail(slug), "contribution-policy"] as const,
  managementPolicy: (slug: string) =>
    [...communityKeys.detail(slug), "management-policy"] as const,
  communicationSettings: (slug: string) =>
    [...communityKeys.detail(slug), "communication-settings"] as const,
  chatDestinations: (slug: string) =>
    [...communityKeys.detail(slug), "chat-destinations"] as const,
};

export const communityKindKeys = {
  all: ["community-kinds"] as const,
  list: () => [...communityKindKeys.all, "list"] as const,
};

/** Declared in a query's `meta` by a read outside this slice that shows a community's name or
 *  description (in a hub they are the organization's public card), so an update refreshes it. */
export const FOLLOWS_COMMUNITY_UPDATES = {
  followsCommunityUpdates: true,
} as const;

export function followsCommunityUpdates(query: Query): boolean {
  return query.meta?.followsCommunityUpdates === true;
}
