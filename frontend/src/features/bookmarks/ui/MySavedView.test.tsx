import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useParams } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { SavedItem } from "@/features/bookmarks/domain/saved";
import { view } from "@/features/bookmarks/domain/saved-filter-fixture";
import { MySavedView } from "@/features/bookmarks/ui/MySavedView";
import { useCommunity } from "@/features/community/api/use-communities";

vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useParams: vi.fn(() => ({})),
}));

function onPage(community?: { id: number; slug: string }) {
  vi.mocked(useParams).mockReturnValue(
    community === undefined ? {} : { slug: community.slug },
  );
  vi.mocked(useCommunity).mockReturnValue({
    data: community === undefined ? undefined : { id: community.id },
  } as never);
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  onPage();
});

const markHelpful = vi.fn();
const unsave = vi.fn();
let items: SavedItem[] = [];
let isLoading = false;

vi.mock("@/features/bookmarks/api/use-save", () => ({
  useMySaved: () => ({ data: items, isLoading }),
  useMarkHelpful: () => ({ mutate: markHelpful, isPending: false }),
  useUnsave: () => ({ mutate: unsave, isPending: false }),
}));

vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: () => ({
    data: [
      {
        id: 3,
        label: "RFC",
        icon: "file-text",
        accentColor: "#123456",
        artifactCount: 0,
      },
    ],
  }),
}));

vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: () => ({ data: {} }),
}));

function renderView() {
  return render(
    <MemoryRouter>
      <MySavedView />
    </MemoryRouter>,
  );
}

describe("MySavedView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    items = [];
    isLoading = false;
  });

  it("shows the empty state when nothing is saved", () => {
    renderView();
    expect(screen.getByText(/nothing saved yet/i)).toBeInTheDocument();
  });

  it("asks 'was this helpful?' on an unanswered row", () => {
    items = [view()];
    renderView();
    expect(screen.getByText(/was this helpful/i)).toBeInTheDocument();
  });

  it("marks helpful when the toggle is pressed", async () => {
    items = [view()];
    renderView();
    await user.click(screen.getByRole("button", { name: /helpful/i }));
    expect(markHelpful).toHaveBeenCalledWith(true);
  });

  it("shows the marked state and offers no thumbs-down", () => {
    items = [view({ helped: true })];
    renderView();
    expect(screen.getByText(/marked helpful/i)).toBeInTheDocument();
    expect(screen.queryByText(/was this helpful/i)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /not helpful|no/i }),
    ).not.toBeInTheDocument();
  });

  it("clears the mark back to neutral when toggled off", async () => {
    items = [view({ helped: true })];
    renderView();
    await user.click(screen.getByRole("button", { name: /helpful/i }));
    expect(markHelpful).toHaveBeenCalledWith(null);
  });

  it("shows the community badge linking to the community", () => {
    items = [view()];
    renderView();
    const badge = screen.getByRole("link", { name: /backend guild/i });
    expect(badge).toHaveAttribute("href", "/communities/backend-guild");
  });

  it("tints the type icon with the artifact type's configured accent", () => {
    items = [view()];
    const { container } = renderView();
    expect(container.querySelector('[style*="#123456"]')).not.toBeNull();
  });

  it("omits the badge when the community is unresolved", () => {
    items = [view({ community: null })];
    renderView();
    expect(
      screen.queryByRole("link", { name: /backend guild/i }),
    ).not.toBeInTheDocument();
  });

  it("shows no filter for a single community", () => {
    items = [view(), view({ id: 9 })];
    renderView();
    expect(
      screen.queryByRole("button", { name: /community/i }),
    ).not.toBeInTheDocument();
  });

  it("filters the list by community", async () => {
    items = [
      view({ id: 1, title: "Backend talk" }),
      view({
        id: 2,
        title: "Frontend talk",
        communityId: 3,
        community: {
          id: 3,
          slug: "frontend-guild",
          name: "Frontend Guild",
          accentColor: null,
        },
      }),
    ];
    renderView();

    expect(screen.getByText("Backend talk")).toBeInTheDocument();
    expect(screen.getByText("Frontend talk")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /community/i }));
    await user.click(screen.getByRole("option", { name: "Frontend Guild" }));

    expect(screen.queryByText("Backend talk")).not.toBeInTheDocument();
    expect(screen.getByText("Frontend talk")).toBeInTheDocument();
  });

  it("searches by title", async () => {
    items = [
      view({ id: 1, title: "Kafka rebalancing" }),
      view({ id: 2, title: "Trunk based dev" }),
    ];
    renderView();
    await user.type(screen.getByRole("searchbox"), "kafka");
    expect(screen.getByText("Kafka rebalancing")).toBeInTheDocument();
    expect(screen.queryByText("Trunk based dev")).not.toBeInTheDocument();
  });

  it("keeps only to-review items when the toggle is on", async () => {
    items = [
      view({ id: 1, title: "Marked", helped: true }),
      view({ id: 2, title: "Unmarked", helped: null }),
    ];
    renderView();
    await user.click(screen.getByRole("button", { name: /to review/i }));
    expect(screen.queryByText("Marked")).not.toBeInTheDocument();
    expect(screen.getByText("Unmarked")).toBeInTheDocument();
  });

  it("reveals the next page with Show more", async () => {
    items = Array.from({ length: 30 }, (_, i) =>
      view({ id: i + 1, title: `Item ${i + 1}` }),
    );
    renderView();
    expect(screen.queryByText("Item 26")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /show more/i }));
    expect(screen.getByText("Item 26")).toBeInTheDocument();
  });

  it("collapses the window back to the first page when the filters change", async () => {
    items = Array.from({ length: 30 }, (_, i) =>
      view({ id: i + 1, title: `Item ${i + 1}` }),
    );
    renderView();
    await user.click(screen.getByRole("button", { name: /show more/i }));
    expect(screen.getByText("Item 26")).toBeInTheDocument();

    await user.type(screen.getByRole("searchbox"), "Item");

    expect(screen.queryByText("Item 26")).not.toBeInTheDocument();
    expect(screen.getByText("Item 1")).toBeInTheDocument();
  });

  it("unsaves a row", async () => {
    items = [view()];
    renderView();
    await user.click(
      screen.getByRole("button", { name: /remove from saved/i }),
    );
    expect(unsave).toHaveBeenCalled();
  });

  it("shows a filtered-empty state with a clear action", async () => {
    items = [view({ title: "Only match" })];
    renderView();
    await user.type(screen.getByRole("searchbox"), "zzz");
    expect(screen.getByText(/no saved items match/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(screen.getByText("Only match")).toBeInTheDocument();
  });

  it("shows the to-review count in the header", () => {
    items = [view({ helped: true }), view({ id: 2, helped: null })];
    renderView();
    expect(screen.getByText(/1 to review/i)).toBeInTheDocument();
  });
});
