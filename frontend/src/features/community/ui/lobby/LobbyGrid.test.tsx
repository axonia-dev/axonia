import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  useLobbySearch,
  useLobbyShelf,
} from "@/features/community/api/use-lobby";
import { useJoin } from "@/features/community/api/use-membership";
import type { LobbyItem } from "@/features/community/domain/lobby";
import {
  LobbySearchGrid,
  LobbyShelfGrid,
} from "@/features/community/ui/lobby/LobbyGrid";
import { useUsers } from "@/features/identity/api/use-users";
import { lobbyItem } from "@/test/lobby-fixture";

vi.mock("@/features/community/api/use-lobby", () => ({
  useLobbyShelf: vi.fn(),
  useLobbySearch: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useJoin: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const mockUseLobbySearch = vi.mocked(useLobbySearch);
const fetchNextPage = vi.fn();
const refetch = vi.fn();
const originalObserver = window.IntersectionObserver;
const originalMatchMedia = window.matchMedia;

let reach: (() => void) | undefined;

class ReachableObserver {
  constructor(callback: IntersectionObserverCallback) {
    reach = () =>
      callback(
        [{ isIntersecting: true } as IntersectionObserverEntry],
        this as unknown as IntersectionObserver,
      );
  }
  observe() {}
  disconnect() {}
}

function searchState(state: Record<string, unknown>) {
  mockUseLobbySearch.mockReturnValue({
    data: undefined,
    errorUpdateCount: 0,
    hasNextPage: false,
    isFetchingNextPage: false,
    isFetchNextPageError: false,
    isFetching: false,
    fetchNextPage,
    refetch,
    ...state,
  } as never);
}

function results(content: LobbyItem[]) {
  return { pages: [{ content, totalElements: content.length }] };
}

function renderSearch() {
  render(
    <MemoryRouter>
      <LobbySearchGrid q="guild" onOpenThreshold={vi.fn()} />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  reach = undefined;
  vi.mocked(useUsers).mockReturnValue({ data: undefined } as never);
  vi.mocked(useJoin).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
});

afterEach(() => {
  window.IntersectionObserver = originalObserver;
  window.matchMedia = originalMatchMedia;
});

describe("LobbyShelfGrid", () => {
  it("lays the shelf out as a labelled grid of cards", () => {
    // given
    const first = [lobbyItem({ id: 1 }), lobbyItem({ id: 2 })];
    vi.mocked(useLobbyShelf).mockReturnValue({
      data: { pages: [{ items: first, hasMore: false }], pageParams: [0] },
      hasNextPage: false,
      isFetchingNextPage: false,
      fetchNextPage,
    } as never);

    // when
    render(
      <MemoryRouter>
        <LobbyShelfGrid
          shelf={{ key: "OTHERS", items: first, hasMore: false }}
          faces={() => []}
          onOpenThreshold={vi.fn()}
        />
      </MemoryRouter>,
    );

    // then
    expect(
      screen.getByRole("region", { name: "Every other community" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Community 2" }),
    ).toBeInTheDocument();
  });
});

describe("a grid's cards", () => {
  function renderGrid(count: number) {
    const shelf = Array.from({ length: count }, (_, i) =>
      lobbyItem({ id: i + 1 }),
    );
    vi.mocked(useLobbyShelf).mockReturnValue({
      data: { pages: [{ items: shelf, hasMore: false }], pageParams: [0] },
      hasNextPage: false,
      isFetchingNextPage: false,
      isFetchNextPageError: false,
      fetchNextPage,
    } as never);
    render(
      <MemoryRouter>
        <LobbyShelfGrid
          shelf={{ key: "OTHERS", items: shelf, hasMore: false }}
          faces={() => []}
          onOpenThreshold={vi.fn()}
        />
      </MemoryRouter>,
    );
  }

  function growth(name: string): HTMLElement | null {
    return screen.getByRole("button", { name }).parentElement;
  }

  it("grow inward from the outer columns, so none leaves the page", () => {
    // when
    renderGrid(5);

    // then
    expect(growth("Community 1")).toHaveClass("origin-top-left");
    expect(growth("Community 2")).toHaveClass("origin-top");
    expect(growth("Community 4")).toHaveClass("origin-top-right");
    expect(growth("Community 5")).toHaveClass("origin-top-left");
  });

  it("open no preview in a single column, where a grown card would cross the gutter", () => {
    // given
    window.matchMedia = (query: string) =>
      ({
        matches: false,
        media: query,
        addEventListener: () => {},
        removeEventListener: () => {},
      }) as unknown as MediaQueryList;
    renderGrid(2);
    const card = screen
      .getByRole("button", { name: "Community 1" })
      .closest("[data-preview]");
    if (card === null) throw new Error("no card");

    // when
    fireEvent.pointerEnter(card, { pointerType: "mouse" });

    // then
    expect(card).toHaveAttribute("data-preview", "closed");
  });
});

describe("LobbySearchGrid", () => {
  it("shows skeletons while the first matches load", () => {
    // given
    searchState({});

    // when
    renderSearch();

    // then
    expect(screen.getByTestId("lobby-card-skeletons")).toBeInTheDocument();
  });

  it("says no matches when nothing matches", () => {
    // given
    searchState({ data: results([]) });

    // when
    renderSearch();

    // then
    expect(screen.getByText("No matches")).toBeInTheDocument();
  });

  it("offers to try again when the search fails", async () => {
    // given
    searchState({ errorUpdateCount: 1 });
    renderSearch();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not search communities.",
    );
    expect(refetch).toHaveBeenCalled();
  });

  it("shows the matches as cards", () => {
    // given
    searchState({ data: results([lobbyItem({ name: "Backend Guild" })]) });

    // when
    renderSearch();

    // then
    expect(
      screen.getByRole("button", { name: "Backend Guild" }),
    ).toBeInTheDocument();
  });

  it("asks for no next page after one failed, and offers to try again", async () => {
    // given
    window.IntersectionObserver =
      ReachableObserver as unknown as typeof IntersectionObserver;
    searchState({
      data: results([lobbyItem()]),
      hasNextPage: true,
      isFetchNextPageError: true,
    });
    renderSearch();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(reach).toBeUndefined();
    expect(fetchNextPage).toHaveBeenCalledOnce();
  });

  it("loads the next page when the end of the grid comes into view", () => {
    // given
    window.IntersectionObserver =
      ReachableObserver as unknown as typeof IntersectionObserver;
    searchState({ data: results([lobbyItem()]), hasNextPage: true });
    renderSearch();

    // when
    act(() => reach?.());

    // then
    expect(fetchNextPage).toHaveBeenCalledOnce();
  });
});
