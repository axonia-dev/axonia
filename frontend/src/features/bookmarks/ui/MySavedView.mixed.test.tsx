import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { SavedItem } from "@/features/bookmarks/domain/saved";
import {
  sessionView,
  view,
} from "@/features/bookmarks/domain/saved-filter-fixture";
import { MySavedView } from "@/features/bookmarks/ui/MySavedView";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

let items: SavedItem[] = [];

vi.mock("@/features/bookmarks/api/use-save", () => ({
  useMySaved: () => ({ data: items, isLoading: false }),
  useMarkHelpful: () => ({ mutate: vi.fn(), isPending: false }),
  useUnsave: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: () => ({ data: undefined }),
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

describe("MySavedView: mixed artifact/session list", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    items = [];
  });

  it("renders both an artifact row and a session row in a mixed list", () => {
    items = [view(), sessionView()];
    renderView();
    expect(screen.getByText("Virtual threads")).toBeInTheDocument();
    expect(screen.getByText("Kafka office hours")).toBeInTheDocument();
  });

  it("does not show a 'Was this helpful?' prompt for a session row", () => {
    items = [sessionView()];
    renderView();
    expect(screen.queryByText(/was this helpful/i)).not.toBeInTheDocument();
  });

  it("filters down to sessions only via the kind control", async () => {
    items = [view(), sessionView()];
    renderView();

    await user.click(screen.getByRole("button", { name: "Sessions" }));

    expect(screen.queryByText("Virtual threads")).not.toBeInTheDocument();
    expect(screen.getByText("Kafka office hours")).toBeInTheDocument();
  });

  it("filters down to artifacts only via the kind control", async () => {
    items = [view(), sessionView()];
    renderView();

    await user.click(screen.getByRole("button", { name: "Artifacts" }));

    expect(screen.getByText("Virtual threads")).toBeInTheDocument();
    expect(screen.queryByText("Kafka office hours")).not.toBeInTheDocument();
  });

  it("hides TypeChips once the kind control is set to Sessions", async () => {
    items = [view(), sessionView()];
    renderView();

    await user.click(screen.getByRole("button", { name: "Sessions" }));

    expect(
      screen.queryByRole("button", { name: "RFC" }),
    ).not.toBeInTheDocument();
  });

  it("keeps sessions visible when a type chip narrows kind=all", async () => {
    items = [view(), sessionView()];
    renderView();

    await user.click(screen.getByRole("button", { name: "RFC" }));

    expect(screen.getByText("Virtual threads")).toBeInTheDocument();
    expect(screen.getByText("Kafka office hours")).toBeInTheDocument();
  });

  it("counts distinct communities across both artifact and session saves", () => {
    items = [
      view(),
      sessionView({
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
    expect(
      screen.getByRole("button", { name: /community/i }),
    ).toBeInTheDocument();
  });
});
