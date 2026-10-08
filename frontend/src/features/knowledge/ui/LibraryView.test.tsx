import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LibraryView } from "@/features/knowledge/ui/LibraryView";

function renderView(slug = "backend-guild") {
  return render(
    <MemoryRouter>
      <LibraryView slug={slug} />
    </MemoryRouter>,
  );
}

vi.mock("@/features/knowledge/api/use-artifacts", () => ({
  useArtifacts: vi.fn(),
}));
vi.mock("@/features/knowledge/ui/LibraryHeader", () => ({
  LibraryHeader: () => <div data-testid="library-header" />,
}));
vi.mock("@/features/knowledge/ui/TypeChips", () => ({
  ALL_TYPES: 0,
  TypeChips: ({ onSelect }: { onSelect: (id: number) => void }) => (
    <button type="button" onClick={() => onSelect(3)}>
      pick-type
    </button>
  ),
}));
vi.mock("@/features/knowledge/ui/SessionFilter", () => ({
  ANY_SESSION: -1,
  NO_SESSION: 0,
  SessionFilter: ({ onChange }: { onChange: (id: number) => void }) => (
    <>
      <button type="button" onClick={() => onChange(9)}>
        pick-session
      </button>
      <button type="button" onClick={() => onChange(0)}>
        pick-no-session
      </button>
    </>
  ),
}));
vi.mock("@/features/knowledge/ui/SessionContextStrip", () => ({
  SessionContextStrip: ({ sessionId }: { sessionId: number }) => (
    <div data-testid="context-strip">{sessionId}</div>
  ),
}));
vi.mock("@/features/knowledge/ui/ArtifactList", () => ({
  ArtifactList: ({
    artifacts,
    isFiltered,
    onTagClick,
  }: {
    artifacts: { id: number }[];
    isFiltered?: boolean;
    onTagClick?: (tag: string) => void;
  }) => (
    <div data-testid="artifact-list" data-filtered={String(isFiltered)}>
      <span>{artifacts.length}</span>
      <button type="button" onClick={() => onTagClick?.("hexagonal")}>
        click-tag
      </button>
    </div>
  ),
}));

import { useArtifacts } from "@/features/knowledge/api/use-artifacts";

const mockArtifacts = vi.mocked(useArtifacts);

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockArtifacts.mockReturnValue({
    data: {
      content: [{ id: 1 }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    },
    isLoading: false,
    isError: false,
  } as never);
});

describe("LibraryView", () => {
  it("renders the library list from the search hook", () => {
    // when
    renderView();

    // then
    expect(screen.getByTestId("artifact-list")).toHaveTextContent("1");
    expect(mockArtifacts).toHaveBeenCalledWith(
      "backend-guild",
      expect.objectContaining({ page: 0, size: 20 }),
    );
  });

  it("passes the search query to the hook", async () => {
    // given
    renderView();

    // when
    await user.type(
      screen.getByRole("searchbox", { name: /search the library/i }),
      "hexagon",
    );

    // then
    expect(mockArtifacts).toHaveBeenLastCalledWith(
      "backend-guild",
      expect.objectContaining({ q: "hexagon" }),
    );
  });

  it("tells the list a search is narrowing it, but not a sort", async () => {
    // given
    renderView();
    expect(screen.getByTestId("artifact-list")).toHaveAttribute(
      "data-filtered",
      "false",
    );

    // when
    await user.type(
      screen.getByRole("searchbox", { name: /search the library/i }),
      "hexagon",
    );

    // then
    expect(screen.getByTestId("artifact-list")).toHaveAttribute(
      "data-filtered",
      "true",
    );
  });

  it("passes the selected type to the hook", async () => {
    // given
    renderView();

    // when
    await user.click(screen.getByRole("button", { name: "pick-type" }));

    // then
    expect(mockArtifacts).toHaveBeenLastCalledWith(
      "backend-guild",
      expect.objectContaining({ type: 3 }),
    );
  });

  it("filters by a session and shows its context strip", async () => {
    // given
    renderView();

    // when
    await user.click(screen.getByRole("button", { name: "pick-session" }));

    // then
    expect(mockArtifacts).toHaveBeenLastCalledWith(
      "backend-guild",
      expect.objectContaining({ session: 9 }),
    );
    expect(screen.getByTestId("context-strip")).toHaveTextContent("9");
  });

  it("filters standalone artifacts without a context strip", async () => {
    // given
    renderView();

    // when
    await user.click(screen.getByRole("button", { name: "pick-no-session" }));

    // then
    expect(mockArtifacts).toHaveBeenLastCalledWith(
      "backend-guild",
      expect.objectContaining({ session: 0 }),
    );
    expect(screen.queryByTestId("context-strip")).not.toBeInTheDocument();
  });

  it("switches the ordering to most appreciated", async () => {
    // given: newest is the default, sent as no sort param
    renderView();
    expect(mockArtifacts).toHaveBeenLastCalledWith(
      "backend-guild",
      expect.not.objectContaining({ sort: "mostAppreciated" }),
    );

    // when
    await user.click(screen.getByRole("button", { name: /sort/i }));
    await user.click(screen.getByRole("option", { name: "Most loved" }));

    // then
    expect(mockArtifacts).toHaveBeenLastCalledWith(
      "backend-guild",
      expect.objectContaining({ sort: "mostAppreciated", page: 0 }),
    );
  });

  it("filters by a clicked tag and clears it again", async () => {
    // given
    renderView();

    // when
    await user.click(screen.getByRole("button", { name: "click-tag" }));

    // then
    expect(mockArtifacts).toHaveBeenLastCalledWith(
      "backend-guild",
      expect.objectContaining({ tag: "hexagonal" }),
    );

    // when: clearing the active tag pill
    await user.click(screen.getByRole("button", { name: /#hexagonal/ }));

    // then
    expect(mockArtifacts).toHaveBeenLastCalledWith(
      "backend-guild",
      expect.not.objectContaining({ tag: "hexagonal" }),
    );
  });
});
