import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { describe, expect, it } from "vitest";

import { communityKeys } from "@/features/community/api/query-keys";
import type {
  Community,
  CommunityListing,
} from "@/features/community/domain/community";
import { CommunityTile } from "@/features/community/ui/CommunityTile";

const listing: CommunityListing = {
  id: 1,
  slug: "platform-guild",
  name: "Platform Guild",
  kindId: 2,
  kind: "Guild",
  description: "",
  status: "ACTIVE",
  memberCount: 3,
  role: "ORGANIZER",
  heroUrl: "/media/hero.png",
  heroFocus: { x: 20, y: 70 },
  accentColor: "#e0a33c",
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function renderTile(
  overrides: Partial<CommunityListing> = {},
  queryClient = new QueryClient(),
) {
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Routes>
          <Route
            path="/"
            element={<CommunityTile community={{ ...listing, ...overrides }} />}
          />
          <Route
            path="/communities/:slug"
            element={<div data-testid="home" />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("CommunityTile", () => {
  it("links to the community home and names it, its members and the role", () => {
    // when
    renderTile();

    // then
    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("href", "/communities/platform-guild");
    expect(link).toHaveTextContent("Platform Guild");
    expect(link).toHaveTextContent("3 members");
    expect(link).toHaveTextContent("Organizer");
  });

  it("frames the hero image at its focal point", () => {
    // when
    const { container } = renderTile();

    // then
    expect(container.querySelector("img")).toHaveStyle({
      objectPosition: "20% 70%",
    });
  });

  it("falls back to the accent when there is no hero image", () => {
    // when
    const { container } = renderTile({ heroUrl: null });

    // then
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelector("[data-tile-banner]")).toBeInTheDocument();
  });

  it("fades the banner vertically into the tile surface", () => {
    // when
    const { container } = renderTile();

    // then
    const banner = container.querySelector("[data-tile-banner]");
    expect(banner?.className).toContain("bg-linear-to-b");
    expect(banner?.className).not.toContain("bg-linear-to-br");
  });

  it("navigates to the community home on a plain click and seeds its detail", async () => {
    // given
    const queryClient = new QueryClient();
    renderTile({}, queryClient);

    // when
    fireEvent.click(screen.getByRole("link"), { button: 0 });

    // then
    expect(await screen.findByTestId("home")).toBeInTheDocument();
    expect(
      queryClient.getQueryData<Community>(
        communityKeys.detail("platform-guild"),
      )?.name,
    ).toBe("Platform Guild");
  });
});
