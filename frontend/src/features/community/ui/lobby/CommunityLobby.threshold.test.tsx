import { act, fireEvent, render, screen, within } from "@testing-library/react";
import {
  Link,
  MemoryRouter,
  Route,
  Routes,
  useNavigate,
} from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  advance,
  isInert,
  stubBillboardStage,
} from "@/components/patterns/billboard/billboard.test-helpers";
import {
  useLobby,
  useLobbySearch,
  useLobbyShelf,
} from "@/features/community/api/use-lobby";
import { useJoin } from "@/features/community/api/use-membership";
import type { LobbyItem } from "@/features/community/domain/lobby";
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

stubBillboardStage();

beforeEach(() => {
  vi.mocked(useUsers).mockReturnValue({ data: undefined } as never);
  vi.mocked(useJoin).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useLobbyShelf).mockImplementation(
    (_key, firstPage: LobbyItem[]) =>
      ({
        data: {
          pages: [{ items: firstPage, hasMore: false }],
          pageParams: [0],
        },
        hasNextPage: false,
        isFetchingNextPage: false,
        isFetchNextPageError: false,
        fetchNextPage: vi.fn(),
      }) as never,
  );
  vi.mocked(useLobbySearch).mockReturnValue({ data: undefined } as never);
  vi.mocked(useLobby).mockReturnValue({
    data: lobbyFixture({
      billboard: [
        lobbyItem({ id: 1, name: "Backend Guild" }),
        lobbyItem({ id: 2, name: "Data Guild" }),
      ],
      shelves: [],
    }),
    errorUpdateCount: 0,
    isFetching: false,
    refetch: vi.fn(),
  } as never);
});

/** A browser moves focus into the portalled dialog and the billboard sees it leave; jsdom does not. */
function leaveBillboard(slide: HTMLElement) {
  act(() => {
    slide.dispatchEvent(
      new FocusEvent("focusout", {
        bubbles: true,
        relatedTarget: screen.getByRole("dialog"),
      }),
    );
  });
}

describe("the threshold over the billboard", () => {
  it("holds the billboard still, and hands focus back to the slide that opened it", () => {
    // given
    render(
      <MemoryRouter>
        <CommunityLobby canCreate={false} />
      </MemoryRouter>,
      inQueryClient(),
    );
    const first = screen.getByRole("group", { name: "Backend Guild, 1 of 2" });
    const peek = within(first).getByRole("button", { name: "Peek inside" });
    act(() => peek.focus());
    fireEvent.click(peek);
    expect(
      screen.getByRole("dialog", { name: "Backend Guild" }),
    ).toBeInTheDocument();

    // when
    leaveBillboard(first);
    advance(30_000);
    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });
    advance(1000);

    // then
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(isInert(first)).toBe(false);
    expect(document.activeElement).toBe(peek);
  });
});

describe("coming back to the lobby", () => {
  function Elsewhere() {
    const navigate = useNavigate();
    return (
      <button type="button" onClick={() => void navigate(-1)}>
        Back
      </button>
    );
  }

  it("opens the billboard on the slide the person left from", () => {
    // given
    render(
      <MemoryRouter initialEntries={["/communities"]}>
        <Routes>
          <Route
            path="/communities"
            element={
              <>
                <CommunityLobby canCreate={false} />
                <Link to="/communities/data-guild">Leave</Link>
              </>
            }
          />
          <Route path="/communities/:slug" element={<Elsewhere />} />
        </Routes>
      </MemoryRouter>,
      inQueryClient(),
    );
    fireEvent.click(screen.getByRole("button", { name: "Next slide" }));
    expect(
      isInert(screen.getByRole("group", { name: "Data Guild, 2 of 2" })),
    ).toBe(false);

    // when
    fireEvent.click(screen.getByRole("link", { name: "Leave" }));
    fireEvent.click(screen.getByRole("button", { name: "Back" }));

    // then
    expect(
      isInert(screen.getByRole("group", { name: "Data Guild, 2 of 2" })),
    ).toBe(false);
  });
});
