import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { Community } from "@/features/community/domain/community";
import { CommunityHeroCard } from "@/features/community/ui/CommunityHeroCard";
import { kindHue } from "@/lib/kind-hue";

const base: Community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "Patterns and practices.",
  status: "ACTIVE",
  memberCount: 120,
  badgeUrl: null,
  heroUrl: null,
  accentColor: null,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

describe("CommunityHeroCard", () => {
  it("renders identity and a footer slot", () => {
    // when
    render(<CommunityHeroCard community={base} footer={<div>join-cta</div>} />);

    // then
    expect(
      screen.getByRole("heading", { name: "Backend Guild" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Patterns and practices.")).toBeInTheDocument();
    expect(screen.getByText("join-cta")).toBeInTheDocument();
  });

  it("counts a single member in the singular", () => {
    // when
    render(<CommunityHeroCard community={{ ...base, memberCount: 1 }} />);

    // then
    expect(
      screen.getByText((_, node) => node?.textContent === "1 member"),
    ).toBeInTheDocument();
  });

  it("shows the badge image when a badgeUrl is set", () => {
    // given
    const community = { ...base, badgeUrl: "https://cf/badge.png" };

    // when
    render(<CommunityHeroCard community={community} />);

    // then
    expect(screen.getByRole("img")).toHaveAttribute(
      "src",
      "https://cf/badge.png",
    );
  });

  it("renders edit overlays when editing", () => {
    // when
    render(
      <CommunityHeroCard
        community={base}
        editing
        bannerOverlay={<div>banner-overlay</div>}
        badgeOverlay={<div>badge-overlay</div>}
      />,
    );

    // then
    expect(screen.getByText("banner-overlay")).toBeInTheDocument();
    expect(screen.getByText("badge-overlay")).toBeInTheDocument();
  });

  it("positions the banner at the stored focal point", () => {
    // when
    const { container } = render(
      <CommunityHeroCard
        community={{
          ...base,
          heroUrl: "https://cdn/h.jpg",
          heroFocus: { x: 50, y: 78 },
        }}
      />,
    );
    const image = container.querySelector("[data-focal-image]");

    // then
    expect(image).toHaveStyle({ objectPosition: "50% 78%" });
  });

  it("centres a banner whose point was never set", () => {
    // when
    const { container } = render(
      <CommunityHeroCard
        community={{ ...base, heroUrl: "https://cdn/h.jpg", heroFocus: null }}
      />,
    );
    const image = container.querySelector("[data-focal-image]");

    // then
    expect(image).toHaveStyle({ objectPosition: "50% 50%" });
  });

  it("positions the badge at its own stored point", () => {
    // when
    render(
      <CommunityHeroCard
        community={{
          ...base,
          badgeUrl: "https://cdn/b.jpg",
          badgeFocus: { x: 20, y: 30 },
        }}
      />,
    );

    // then
    expect(screen.getByAltText(base.name)).toHaveStyle({
      objectPosition: "20% 30%",
    });
  });

  it("draws the community in its kind's hue when no accent is set", () => {
    // when
    const { container } = render(
      <CommunityHeroCard
        community={{ ...base, accentColor: null, kindId: 3 }}
      />,
    );
    const section = container.querySelector("section");

    // then
    expect(section?.style.getPropertyValue("--community-accent")).toBe(
      kindHue("3"),
    );
  });
});
