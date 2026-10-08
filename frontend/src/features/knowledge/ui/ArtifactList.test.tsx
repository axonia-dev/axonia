import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSessions } from "@/features/events/api/use-sessions";
import { useUsers } from "@/features/identity/api/use-users";
import { useArtifactTypes } from "@/features/knowledge/api/use-artifact-types";
import type { Artifact } from "@/features/knowledge/domain/artifact";
import { ArtifactList } from "@/features/knowledge/ui/ArtifactList";

vi.mock("@/features/knowledge/ui/ArtifactCard", () => ({
  ArtifactCard: ({
    artifact,
    manageActions,
  }: {
    artifact: { id: number; title: string };
    manageActions?: React.ReactNode;
  }) => (
    <div data-testid="artifact-card">
      {artifact.id}
      <span>{artifact.title}</span>
      {manageActions && <div data-testid="manage-actions">{manageActions}</div>}
    </div>
  ),
}));
vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: vi.fn(() => ({ data: {} })),
}));
vi.mock("@/features/events/api/use-sessions", () => ({
  useSessions: vi.fn(() => ({ data: [] })),
}));
vi.mock("@/features/events/api/use-series", () => ({
  useSeriesList: vi.fn(() => ({ data: { content: [] } })),
}));
vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: vi.fn(() => ({ data: [] })),
}));

const artifact = { id: 1 } as Artifact;

let scrollIntoViewSpy: ReturnType<typeof vi.fn>;

const focusArtifact = {
  id: 42,
  typeId: 1,
  type: "Note",
  title: "Focus me",
  description: "",
  url: "https://example.com",
  tags: [],
  contributorUserId: "u1",
  loveCount: 0,
  provenance: null,
  createdAt: "2024-01-01T00:00:00Z",
};

beforeEach(() => {
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
  vi.mocked(useSessions).mockReturnValue({ data: [] } as never);
  vi.mocked(useArtifactTypes).mockReturnValue({ data: [] } as never);
  scrollIntoViewSpy = vi.fn();
  Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
    writable: true,
    value: scrollIntoViewSpy,
  });
});

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("ArtifactList", () => {
  it("shows the empty state when there are no artifacts", () => {
    // when
    render(
      <ArtifactList
        slug="eng"
        artifacts={[]}
        isLoading={false}
        isError={false}
      />,
    );

    // then
    expect(screen.getByText(/nothing in the library yet/i)).toBeInTheDocument();
  });

  it("says nothing matches, rather than that the library is empty, when filtered", () => {
    // when
    render(
      <ArtifactList
        slug="eng"
        artifacts={[]}
        isLoading={false}
        isError={false}
        isFiltered
      />,
    );

    // then
    expect(screen.getByText("No matching artifacts")).toBeInTheDocument();
    expect(
      screen.queryByText(/nothing in the library yet/i),
    ).not.toBeInTheDocument();
  });

  it("renders a card per artifact", () => {
    // when
    render(
      <ArtifactList
        slug="eng"
        artifacts={[artifact]}
        isLoading={false}
        isError={false}
      />,
    );

    // then
    expect(screen.getAllByTestId("artifact-card")).toHaveLength(1);
  });

  it("shows an error alert when loading failed", () => {
    // when
    render(
      <ArtifactList slug="eng" artifacts={[]} isLoading={false} isError />,
    );

    // then
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("passes manageActions from renderArtifactManageActions to each ArtifactCard", () => {
    // when
    const renderManageActions = vi.fn((artifact: Artifact) => (
      <span data-testid={`manage-actions-${artifact.id}`}>Manage</span>
    ));
    render(
      <ArtifactList
        slug="eng"
        artifacts={[artifact]}
        isLoading={false}
        isError={false}
        renderArtifactManageActions={renderManageActions}
      />,
    );

    // then
    expect(renderManageActions).toHaveBeenCalledWith(artifact);
    expect(screen.getByTestId("manage-actions-1")).toBeInTheDocument();
  });
});

describe("ArtifactList focus", () => {
  it("rings the focused artifact and scrolls it into view", () => {
    render(
      <ArtifactList
        slug="guild"
        artifacts={[focusArtifact as never]}
        isLoading={false}
        isError={false}
        focusedId={42}
      />,
      { wrapper },
    );
    const item = screen.getByText("Focus me").closest("li");
    expect(item?.className).toContain("ring-primary");
    expect(scrollIntoViewSpy).toHaveBeenCalled();
  });
});
