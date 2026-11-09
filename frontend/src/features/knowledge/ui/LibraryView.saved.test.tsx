import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Artifact } from "@/features/knowledge/domain/artifact";
import { LibraryView } from "@/features/knowledge/ui/LibraryView";

vi.mock("@/features/knowledge/api/use-artifacts", () => ({
  useArtifacts: vi.fn(),
}));
vi.mock("@/features/knowledge/ui/LibraryHeader", () => ({
  LibraryHeader: () => null,
}));
vi.mock("@/features/knowledge/ui/TypeChips", () => ({
  ALL_TYPES: 0,
  TypeChips: () => null,
}));
vi.mock("@/features/knowledge/ui/SessionFilter", () => ({
  ANY_SESSION: -1,
  NO_SESSION: 0,
  SessionFilter: () => null,
}));
vi.mock("@/features/bookmarks/ui/SavedHelpfulPrompt", () => ({
  SavedHelpfulPrompt: ({ artifactId }: { artifactId: number }) => (
    <span>helpful-{artifactId}</span>
  ),
}));
vi.mock("@/features/knowledge/ui/ArtifactList", () => ({
  ArtifactList: ({
    empty,
    renderArtifactFootnote,
  }: {
    empty?: { title: string };
    renderArtifactFootnote?: (artifact: Artifact) => ReactNode;
  }) => (
    <div data-testid="artifact-list">
      <span>{empty?.title ?? "default-empty"}</span>
      {renderArtifactFootnote?.({ id: 7 } as Artifact)}
    </div>
  ),
}));

import { useArtifacts } from "@/features/knowledge/api/use-artifacts";

const mockArtifacts = vi.mocked(useArtifacts);

function LocationProbe() {
  return <span data-testid="search">{useLocation().search}</span>;
}

function renderView(entry = "/communities/guild/library") {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <LibraryView slug="guild" />
      <LocationProbe />
    </MemoryRouter>,
  );
}

function mockPages(totalPages: number) {
  mockArtifacts.mockReturnValue({
    data: { content: [], totalElements: 0, totalPages, number: 0, size: 20 },
    isLoading: false,
    isError: false,
  } as never);
}

function lastParams() {
  return mockArtifacts.mock.calls.at(-1)![1];
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockArtifacts.mockReturnValue({
    data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 },
    isLoading: false,
    isError: false,
  } as never);
});

describe("LibraryView saved filter", () => {
  it("searches everything by default, with no saved footnote", () => {
    renderView();

    expect(lastParams()).not.toHaveProperty("saved");
    expect(screen.getByRole("button", { name: "Saved" })).toHaveAttribute(
      "aria-pressed",
      "false",
    );
    expect(screen.getByText("default-empty")).toBeInTheDocument();
    expect(screen.queryByText("helpful-7")).not.toBeInTheDocument();
  });

  it("narrows to saved artifacts from ?saved=1, with the helpful prompt and saved copy", () => {
    renderView("/communities/guild/library?saved=1");

    expect(lastParams()).toMatchObject({ saved: true });
    expect(screen.getByRole("button", { name: "Saved" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByText("Nothing saved here yet")).toBeInTheDocument();
    expect(screen.getByText("helpful-7")).toBeInTheDocument();
  });

  it("starts the saved list on its first page, wherever the browsing had reached", async () => {
    mockPages(3);
    renderView();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(lastParams()).toMatchObject({ page: 1 });

    await user.click(screen.getByRole("button", { name: "Saved" }));

    expect(lastParams()).toMatchObject({ saved: true, page: 0 });
  });

  it("falls back to the last page left when an unsave empties the one in view", async () => {
    mockPages(2);
    const { rerender } = renderView("/communities/guild/library?saved=1");
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(lastParams()).toMatchObject({ page: 1 });

    mockPages(1);
    rerender(
      <MemoryRouter initialEntries={["/communities/guild/library?saved=1"]}>
        <LibraryView slug="guild" />
        <LocationProbe />
      </MemoryRouter>,
    );

    expect(lastParams()).toMatchObject({ page: 0 });
  });

  it("toggles the saved filter through the URL", async () => {
    renderView();

    await user.click(screen.getByRole("button", { name: "Saved" }));
    expect(screen.getByTestId("search")).toHaveTextContent("?saved=1");
    expect(lastParams()).toMatchObject({ saved: true, page: 0 });

    await user.click(screen.getByRole("button", { name: "Saved" }));
    expect(screen.getByTestId("search")).toBeEmptyDOMElement();
    expect(lastParams()).not.toHaveProperty("saved");
  });
});
