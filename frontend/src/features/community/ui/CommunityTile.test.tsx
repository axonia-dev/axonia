import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import type { CommunityListing } from "@/features/community/domain/community";
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

function renderTile(overrides: Partial<CommunityListing> = {}) {
  return render(
    <MemoryRouter>
      <CommunityTile community={{ ...listing, ...overrides }} />
    </MemoryRouter>,
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
});
