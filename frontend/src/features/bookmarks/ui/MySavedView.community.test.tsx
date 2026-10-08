import { render, screen } from "@testing-library/react";
import { MemoryRouter, useParams } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { SavedItem } from "@/features/bookmarks/domain/saved";
import {
  sessionView,
  view,
} from "@/features/bookmarks/domain/saved-filter-fixture";
import { MySavedView } from "@/features/bookmarks/ui/MySavedView";
import { useCommunity } from "@/features/community/api/use-communities";

let items: SavedItem[] = [];

vi.mock("@/features/bookmarks/api/use-save", () => ({
  useMySaved: () => ({ data: items, isLoading: false }),
  useMarkHelpful: () => ({ mutate: vi.fn(), isPending: false }),
  useUnsave: () => ({ mutate: vi.fn(), isPending: false }),
}));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useParams: vi.fn(() => ({})),
}));
vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: () => ({ data: [] }),
}));
vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: () => ({ data: {} }),
}));

function onPage(community: { id: number; slug: string }) {
  vi.mocked(useParams).mockReturnValue({ slug: community.slug });
  vi.mocked(useCommunity).mockReturnValue({
    data: { id: community.id },
  } as never);
}

function renderView() {
  return render(
    <MemoryRouter>
      <MySavedView />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  items = [];
});

describe("MySavedView inside a community", () => {
  const BACKEND_GUILD = { id: 2, slug: "backend-guild" };
  const FRONTEND = {
    id: 3,
    slug: "frontend-guild",
    name: "Frontend Guild",
    accentColor: null,
  };

  it("shows only that community's saved items", () => {
    onPage(BACKEND_GUILD);
    items = [
      view({ title: "Backend talk" }),
      view({
        id: 2,
        title: "Frontend talk",
        communityId: 3,
        community: FRONTEND,
      }),
    ];
    renderView();
    expect(screen.getByText("Backend talk")).toBeInTheDocument();
    expect(screen.queryByText("Frontend talk")).not.toBeInTheDocument();
  });

  it("drops the community badge every row would share with the page", () => {
    onPage(BACKEND_GUILD);
    items = [view({ title: "Backend talk" }), sessionView({ title: "Retro" })];
    renderView();
    expect(screen.getByText("Backend talk")).toBeInTheDocument();
    expect(screen.getByText("Retro")).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /backend guild/i }),
    ).not.toBeInTheDocument();
  });

  it("reflects the narrowed count in the header", () => {
    onPage(BACKEND_GUILD);
    items = [view(), view({ id: 2, communityId: 3, community: FRONTEND })];
    renderView();
    expect(screen.getByText(/1 saved/i)).toBeInTheDocument();
  });

  it("shows a community-scoped empty state when this community has nothing saved yet, even though another does", () => {
    onPage(BACKEND_GUILD);
    items = [view({ communityId: 3, community: FRONTEND })];
    renderView();
    expect(
      screen.getByText(/nothing saved in this community yet/i),
    ).toBeInTheDocument();
  });

  it("waits for the community rather than claiming nothing is saved in it", () => {
    vi.mocked(useParams).mockReturnValue({ slug: BACKEND_GUILD.slug });
    vi.mocked(useCommunity).mockReturnValue({
      data: undefined,
      isLoading: true,
    } as never);
    items = [view()];
    renderView();
    expect(screen.getByText("Loading…")).toBeInTheDocument();
    expect(screen.queryByText(/nothing saved/i)).not.toBeInTheDocument();
  });
});
