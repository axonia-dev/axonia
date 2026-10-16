import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { communityKeys } from "@/features/community/api/query-keys";
import { useCommunities } from "@/features/community/api/use-communities";
import type {
  Community,
  CommunityListing,
} from "@/features/community/domain/community";
import { CommunityList } from "@/features/community/ui/CommunityList";
import { morphNames } from "@/test/morph-names";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  ViewTransition: (await import("@/test/morph-names")).ViewTransitionProbe,
}));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunities: vi.fn(),
}));
vi.mock("@/features/community/ui/ExploreJoinButton", () => ({
  ExploreJoinButton: () => <button type="button">Join</button>,
}));

const listing: CommunityListing = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 2,
  kind: "Guild",
  description: "",
  status: "ACTIVE",
  memberCount: 3,
  role: null,
  badgeUrl: "/media/badge.png",
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function renderExplore(
  overrides: Partial<CommunityListing> = {},
  queryClient = new QueryClient(),
) {
  vi.mocked(useCommunities).mockImplementation(
    (params) =>
      ({
        data: {
          content:
            params.membership === "JOINED"
              ? []
              : [{ ...listing, ...overrides }],
          number: 0,
          totalPages: 1,
          totalElements: 1,
          size: 20,
        },
        isLoading: false,
        isError: false,
        errorUpdateCount: 0,
        refetch: vi.fn(),
      }) as never,
  );
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Routes>
          <Route path="/" element={<CommunityList />} />
          <Route
            path="/communities/:slug"
            element={<div data-testid="home" />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("CommunityList explore row", () => {
  it("names its badge and name as the morph source into the community", () => {
    // when
    const { container } = renderExplore();

    // then
    expect(morphNames(container)).toEqual([
      "morph-badge-community-backend-guild",
      "morph-name-community-backend-guild",
    ]);
  });

  it("leaves the badge unnamed without a badge image", () => {
    // when
    const { container } = renderExplore({ badgeUrl: null });

    // then
    expect(morphNames(container)).toEqual([
      "morph-name-community-backend-guild",
    ]);
  });

  it("navigates to the community home on a plain click and seeds its detail", async () => {
    // given
    const queryClient = new QueryClient();
    renderExplore({}, queryClient);
    const link = screen.getByRole("link", { name: /backend guild/i });
    expect(link).toHaveAttribute("href", "/communities/backend-guild");

    // when
    fireEvent.click(link, { button: 0 });

    // then
    expect(await screen.findByTestId("home")).toBeInTheDocument();
    expect(
      queryClient.getQueryData<Community>(communityKeys.detail("backend-guild"))
        ?.name,
    ).toBe("Backend Guild");
  });
});
