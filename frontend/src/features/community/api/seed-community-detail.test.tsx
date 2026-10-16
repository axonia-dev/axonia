import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";

import { communityKeys } from "@/features/community/api/query-keys";
import { useSeedCommunityDetail } from "@/features/community/api/seed-community-detail";
import type {
  Community,
  CommunityListing,
} from "@/features/community/domain/community";

const community: Community = {
  id: 1,
  slug: "platform-guild",
  name: "Platform Guild",
  kindId: 2,
  kind: "Guild",
  description: "",
  status: "ACTIVE",
  memberCount: 3,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

const listing: CommunityListing = { ...community, role: "MEMBER" };
const key = communityKeys.detail(community.slug);

function renderSeed(queryClient: QueryClient) {
  const wrapper = ({ children }: Readonly<{ children: ReactNode }>) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return renderHook(() => useSeedCommunityDetail(), { wrapper }).result.current;
}

describe("useSeedCommunityDetail", () => {
  it("fills an empty detail entry with the listing minus its role, left stale", async () => {
    // given
    const queryClient = new QueryClient();
    const seed = renderSeed(queryClient);

    // when
    await seed(listing);

    // then
    expect(queryClient.getQueryData(key)).toEqual(community);
    expect(queryClient.getQueryState(key)?.dataUpdatedAt).toBe(0);
  });

  it("never overwrites an existing detail entry", async () => {
    // given
    const queryClient = new QueryClient();
    queryClient.setQueryData(key, { ...community, name: "Fresh" });
    const seed = renderSeed(queryClient);

    // when
    await seed(listing);

    // then
    expect(queryClient.getQueryData<Community>(key)?.name).toBe("Fresh");
  });
});
