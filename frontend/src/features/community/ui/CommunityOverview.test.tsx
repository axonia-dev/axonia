import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import { ChatChannelWarning } from "@/features/community/ui/ChatChannelWarning";
import { CommunityOverview } from "@/features/community/ui/CommunityOverview";
import { IdentityHero } from "@/features/community/ui/IdentityHero";

vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));
vi.mock("@/features/community/ui/IdentityHero", () => ({
  IdentityHero: vi.fn(() => <div>identity-hero</div>),
}));
vi.mock("@/features/community/ui/ChatChannelWarning", () => ({
  ChatChannelWarning: vi.fn(() => null),
}));
vi.mock("@/features/community/ui/SessionMarquee", () => ({
  SessionMarquee: () => <div>session-marquee</div>,
}));
vi.mock("@/features/community/ui/LibraryFeed", () => ({
  LibraryFeed: () => (
    <div>
      <h2>From the Library</h2>library-feed
    </div>
  ),
}));
vi.mock("@/features/community/ui/CommunityPulse", () => ({
  CommunityPulse: () => (
    <div>
      <h2>Community pulse</h2>community-pulse
    </div>
  ),
}));
vi.mock("@/features/community/ui/MostLovedFeed", () => ({
  MostLovedFeed: () => <div>most-loved-feed</div>,
}));
vi.mock("@/features/community/ui/TrendingTags", () => ({
  TrendingTags: () => <div>trending-tags</div>,
}));
vi.mock("@/features/community/ui/NewMembers", () => ({
  NewMembers: () => <div>new-members</div>,
}));

const activeCommunity = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "Patterns and practices.",
  status: "ACTIVE",
  memberCount: 120,
};

function renderOverview() {
  render(
    <MemoryRouter initialEntries={["/communities/backend-guild"]}>
      <Routes>
        <Route path="/communities/:slug" element={<CommunityOverview />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("CommunityOverview", () => {
  beforeEach(() => {
    vi.mocked(useCommunity).mockReturnValue({
      isLoading: false,
      isError: false,
      data: activeCommunity,
    } as never);
  });

  it("composes the hero, the marquee, the Library feed and the pulse rail", () => {
    // when
    renderOverview();

    // then
    expect(screen.getByText("identity-hero")).toBeInTheDocument();
    expect(screen.getByText("session-marquee")).toBeInTheDocument();
    expect(screen.getByText("library-feed")).toBeInTheDocument();
    expect(screen.getByText("community-pulse")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "From the Library" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Community pulse" }),
    ).toBeInTheDocument();
  });

  it("passes the community and slug to the hero", () => {
    // when
    renderOverview();

    // then
    expect(vi.mocked(IdentityHero).mock.calls[0]?.[0]).toMatchObject({
      slug: "backend-guild",
      community: activeCommunity,
    });
  });

  it("gives the chat channel warning the community's slug", () => {
    // when
    renderOverview();

    // then
    expect(vi.mocked(ChatChannelWarning).mock.calls[0]?.[0]).toMatchObject({
      slug: "backend-guild",
    });
  });

  it("shows a loading state while fetching", () => {
    // given
    vi.mocked(useCommunity).mockReturnValue({
      isLoading: true,
      isError: false,
      data: undefined,
    } as never);

    // when
    renderOverview();

    // then
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });

  it("shows an error state when the community fails to load", () => {
    // given
    vi.mocked(useCommunity).mockReturnValue({
      isLoading: false,
      isError: true,
      data: undefined,
    } as never);

    // when
    renderOverview();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load this community.",
    );
  });
});
