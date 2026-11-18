import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { handOffCeremony } from "@/features/community/api/ceremony-handoff";
import {
  useLobby,
  useLobbySearch,
  useLobbyShelf,
} from "@/features/community/api/use-lobby";
import { useJoin } from "@/features/community/api/use-membership";
import type { Lobby, LobbyItem } from "@/features/community/domain/lobby";
import { CommunityLobby } from "@/features/community/ui/lobby/CommunityLobby";
import { useUsers } from "@/features/identity/api/use-users";
import { lobbyFixture, lobbyItem } from "@/test/lobby-fixture";
import { inQueryClient } from "@/test/query-client";

vi.mock("@/features/community/api/use-lobby", () => ({
  useLobby: vi.fn(),
  useLobbyShelf: vi.fn(),
  useLobbySearch: vi.fn(),
  useShelfSlugs: () => new Map(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useJoin: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));
vi.mock("@/features/community/api/ceremony-handoff", () => ({
  handOffCeremony: vi.fn(),
}));

const mockUseLobby = vi.mocked(useLobby);
const mockUseUsers = vi.mocked(useUsers);
const mutate = vi.fn();
const refetch = vi.fn();
const joinedCallbacks = new Map<string, () => void | Promise<void>>();

let user: ReturnType<typeof userEvent.setup>;

const featured = lobbyItem({ id: 1, name: "Backend Guild", faces: ["alice"] });
const mine = lobbyItem({
  id: 2,
  name: "Frontend Guild",
  role: "MEMBER",
  faces: ["bob"],
});
const growing = lobbyItem({ id: 3, name: "Data Guild", faces: ["alice"] });

function lobbyState(state: Record<string, unknown>) {
  mockUseLobby.mockReturnValue({
    data: undefined,
    errorUpdateCount: 0,
    isFetching: false,
    refetch,
    ...state,
  } as never);
}

function fullLobby(overrides: Partial<Lobby> = {}): Lobby {
  return lobbyFixture({
    billboard: [featured],
    shelves: [
      { key: "JOINED", items: [mine], hasMore: false },
      { key: "THIS_WEEK", items: [growing], hasMore: false },
      { key: "GROWING", items: [growing], hasMore: false },
      { key: "OTHERS", items: [featured], hasMore: false },
    ],
    ...overrides,
  });
}

function renderLobby(canCreate = true) {
  render(
    <MemoryRouter initialEntries={["/communities"]}>
      <Routes>
        <Route
          path="/communities"
          element={<CommunityLobby canCreate={canCreate} />}
        />
        <Route path="/communities/:slug" element={<p>community home</p>} />
      </Routes>
    </MemoryRouter>,
    inQueryClient(),
  );
}

function billboard() {
  return screen.getByRole("region", { name: "Featured communities" });
}

beforeEach(() => {
  vi.clearAllMocks();
  joinedCallbacks.clear();
  user = userEvent.setup({ delay: null });
  mockUseUsers.mockReturnValue({ data: undefined } as never);
  vi.mocked(useJoin).mockImplementation((slug, onJoined) => {
    if (onJoined !== undefined) joinedCallbacks.set(slug, onJoined);
    return { mutate, isPending: false, isError: false } as never;
  });
  vi.mocked(useLobbyShelf).mockImplementation(
    (_key, firstPage: LobbyItem[], hasMore: boolean) =>
      ({
        data: { pages: [{ items: firstPage, hasMore }], pageParams: [0] },
        hasNextPage: hasMore,
        isFetchingNextPage: false,
        fetchNextPage: vi.fn(),
      }) as never,
  );
  vi.mocked(useLobbySearch).mockReturnValue({
    data: { pages: [{ content: [growing], totalElements: 1 }] },
    errorUpdateCount: 0,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: vi.fn(),
  } as never);
});

afterEach(() => {
  document.querySelector("[data-ceremony-curtain]")?.remove();
});

describe("CommunityLobby", () => {
  it("lays out the billboard, then the search, then the shelves in their order", () => {
    // given
    lobbyState({ data: fullLobby() });

    // when
    renderLobby();

    // then
    const headings = screen.getAllByRole("heading", { level: 2 }).slice(1);
    const sequence = [billboard(), screen.getByRole("searchbox"), ...headings];
    sequence.slice(1).forEach((element, i) => {
      expect(
        (sequence.at(i)?.compareDocumentPosition(element) ?? 0) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    });
    expect(headings.map((heading) => heading.textContent)).toEqual([
      "Joined",
      "Sessions this week",
      "Growing fast",
      "Every other community",
    ]);
  });

  it("collapses a small organization's discovery into one Explore grid", () => {
    // given
    lobbyState({
      data: fullLobby({
        shelves: [
          { key: "JOINED", items: [mine], hasMore: false },
          { key: "EXPLORE", items: [growing], hasMore: false },
        ],
      }),
    });

    // when
    renderLobby();

    // then
    expect(screen.getByRole("region", { name: "Explore" })).toBeInTheDocument();
  });

  it("joins from a billboard slide and departs to the welcome", async () => {
    // given
    lobbyState({ data: fullLobby() });
    renderLobby();

    // when
    await user.click(within(billboard()).getByRole("button", { name: "Join" }));
    await act(async () => {
      await joinedCallbacks.get("community-1")?.();
    });

    // then
    expect(mutate).toHaveBeenCalledOnce();
    expect(handOffCeremony).toHaveBeenCalledWith("welcome", "community-1");
    expect(await screen.findByText("community home")).toBeInTheDocument();
  });

  it("offers Open on a slide featuring a joined community", () => {
    // given
    lobbyState({ data: fullLobby({ billboard: [mine] }) });

    // when
    renderLobby();

    // then
    expect(
      within(billboard()).getByRole("link", { name: "Open" }),
    ).toBeInTheDocument();
  });

  it("trades the billboard and shelves for the matches while searching", async () => {
    // given
    lobbyState({ data: fullLobby() });
    renderLobby();

    // when
    await user.type(screen.getByRole("searchbox"), "data");

    // then
    expect(
      screen.queryByRole("region", { name: "Featured communities" }),
    ).toBeNull();
    expect(screen.queryByRole("region", { name: "Joined" })).toBeNull();
    expect(screen.getByRole("region", { name: "Matches" })).toBeInTheDocument();
    expect(useLobbySearch).toHaveBeenLastCalledWith("data");
  });

  it("restores the lobby when the search is cleared", async () => {
    // given
    lobbyState({ data: fullLobby() });
    renderLobby();
    await user.type(screen.getByRole("searchbox"), "data");

    // when
    await user.clear(screen.getByRole("searchbox"));

    // then
    expect(billboard()).toBeInTheDocument();
    expect(screen.queryByRole("region", { name: "Matches" })).toBeNull();
  });

  it("shows the create hero and no shelves to an organization with no community", () => {
    // given
    lobbyState({ data: lobbyFixture() });

    // when
    renderLobby();

    // then
    expect(
      screen.getByRole("region", { name: "Start a community here" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("searchbox")).toBeNull();
    expect(screen.queryAllByRole("heading", { level: 2 })).toHaveLength(1);
  });

  it("resolves every face on the page through one directory read", () => {
    // given
    lobbyState({ data: fullLobby() });

    // when
    renderLobby();

    // then
    const reads = new Set(
      mockUseUsers.mock.calls
        .map(([ids]) => ids.join(","))
        .filter((ids) => ids !== ""),
    );
    expect([...reads]).toEqual(["alice,bob"]);
  });

  it("shows skeletons while the lobby loads", () => {
    // given
    lobbyState({});

    // when
    renderLobby();

    // then
    expect(screen.getByTestId("billboard-skeleton")).toBeInTheDocument();
    expect(screen.getByTestId("lobby-card-skeletons")).toBeInTheDocument();
  });

  it("offers to try again when the lobby fails to load", async () => {
    // given
    lobbyState({ errorUpdateCount: 1 });
    renderLobby();

    // when
    await user.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load communities.",
    );
    expect(refetch).toHaveBeenCalled();
  });

  it("opens the threshold of the card clicked", async () => {
    // given
    lobbyState({ data: fullLobby() });
    renderLobby();

    // when
    await user.click(screen.getAllByRole("button", { name: "Data Guild" })[0]!);

    // then
    expect(
      screen.getByRole("dialog", { name: "Data Guild" }),
    ).toBeInTheDocument();
  });
});
