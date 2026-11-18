import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useLobbyShelf } from "@/features/community/api/use-lobby";
import { useJoin } from "@/features/community/api/use-membership";
import type { LobbyItem } from "@/features/community/domain/lobby";
import { LobbyShelfRow } from "@/features/community/ui/lobby/LobbyShelfRow";
import { useUsers } from "@/features/identity/api/use-users";
import { lobbyItem } from "@/test/lobby-fixture";
import { inQueryClient } from "@/test/query-client";

vi.mock("@/features/community/api/use-lobby", () => ({
  useLobbyShelf: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useJoin: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const mockUseLobbyShelf = vi.mocked(useLobbyShelf);
const fetchNextPage = vi.fn();
const originalMatchMedia = window.matchMedia;

function items(from: number, count: number): LobbyItem[] {
  return Array.from({ length: count }, (_, i) =>
    lobbyItem({ id: from + i, name: `Community ${from + i}` }),
  );
}

function mockShelf(
  pages: LobbyItem[][],
  hasNextPage: boolean,
  isFetchNextPageError = false,
) {
  mockUseLobbyShelf.mockReturnValue({
    data: {
      pages: pages.map((page) => ({ items: page, hasMore: hasNextPage })),
      pageParams: pages.map((_, i) => i),
    },
    hasNextPage,
    isFetchingNextPage: false,
    isFetchNextPageError,
    fetchNextPage,
  } as never);
}

function stubWidth(minWidthRem: number) {
  window.matchMedia = (query: string) =>
    ({
      matches: Number(/min-width: (\d+)rem/.exec(query)?.[1]) <= minWidthRem,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }) as unknown as MediaQueryList;
}

function row(key: "JOINED" | "GROWING", first: LobbyItem[]) {
  return (
    <MemoryRouter>
      <LobbyShelfRow
        shelf={{ key, items: first, hasMore: false }}
        faces={() => []}
        onOpenThreshold={vi.fn()}
      />
    </MemoryRouter>
  );
}

function renderRow(key: "JOINED" | "GROWING", first: LobbyItem[]) {
  return render(row(key, first), inQueryClient());
}

function cardNames(): string[] {
  const list = screen.getByRole("list");
  return within(list)
    .getAllByRole("button")
    .map((button) => button.getAttribute("aria-label") ?? "")
    .filter((name) => name.startsWith("Community"));
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useUsers).mockReturnValue({ data: undefined } as never);
  vi.mocked(useJoin).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
});

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

describe("LobbyShelfRow", () => {
  it("labels the shelf by its key", () => {
    // given
    mockShelf([items(1, 2)], false);

    // when
    renderRow("GROWING", items(1, 2));

    // then
    expect(
      screen.getByRole("region", { name: "Growing fast" }),
    ).toBeInTheDocument();
  });

  it("lays every card on a phone's scrolling strip", () => {
    // given
    stubWidth(0);
    mockShelf([items(1, 8), items(9, 2)], false);

    // when
    renderRow("GROWING", items(1, 8));

    // then
    expect(cardNames()).toHaveLength(10);
  });

  it("wears role chips on the Joined shelf only", () => {
    // given
    const joined = [lobbyItem({ role: "ORGANIZER" })];
    mockShelf([joined], false);

    // when
    renderRow("JOINED", joined);

    // then
    expect(screen.getByText("Organizer")).toBeInTheDocument();
  });

  it("shows a window of four cards on a wide screen", () => {
    // given
    stubWidth(64);
    mockShelf([items(1, 8)], false);

    // when
    renderRow("GROWING", items(1, 8));

    // then
    expect(cardNames()).toEqual([
      "Community 1",
      "Community 2",
      "Community 3",
      "Community 4",
    ]);
    expect(
      screen.getByRole("button", { name: "Previous communities" }),
    ).toBeDisabled();
  });

  it("shows three at a time on a medium screen", () => {
    // given
    stubWidth(48);
    mockShelf([items(1, 8)], false);

    // when
    renderRow("GROWING", items(1, 8));

    // then
    expect(cardNames()).toHaveLength(3);
  });

  it("pages to the next window and asks for the next page near the end", async () => {
    // given
    stubWidth(64);
    mockShelf([items(1, 8)], true);
    renderRow("GROWING", items(1, 8));

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "More communities" }),
    );

    // then
    expect(cardNames()).toEqual([
      "Community 5",
      "Community 6",
      "Community 7",
      "Community 8",
    ]);
    expect(fetchNextPage).toHaveBeenCalled();
  });

  it("cannot page past the last card of a complete shelf", () => {
    // given
    stubWidth(64);
    mockShelf([items(1, 4)], false);

    // when
    renderRow("GROWING", items(1, 4));

    // then
    expect(
      screen.getByRole("button", { name: "More communities" }),
    ).toBeDisabled();
    expect(fetchNextPage).not.toHaveBeenCalled();
  });

  it("stops asking for the next page once one fails, until the person asks again", async () => {
    // given
    stubWidth(64);
    mockShelf([items(1, 8)], true, true);
    renderRow("GROWING", items(1, 8));

    // when
    await userEvent.click(
      screen.getByRole("button", { name: "More communities" }),
    );

    // then
    expect(fetchNextPage).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load more communities.",
    );

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(fetchNextPage).toHaveBeenCalledOnce();
  });

  it("keeps its window on cards when the shelf shrinks under it", async () => {
    // given
    stubWidth(64);
    mockShelf([items(1, 8)], false);
    const { rerender } = renderRow("GROWING", items(1, 8));
    await userEvent.click(
      screen.getByRole("button", { name: "More communities" }),
    );

    // when
    mockShelf([items(1, 6)], false);
    rerender(row("GROWING", items(1, 6)));

    // then
    expect(cardNames()).toEqual([
      "Community 3",
      "Community 4",
      "Community 5",
      "Community 6",
    ]);
    expect(
      screen.getByRole("button", { name: "More communities" }),
    ).toBeDisabled();
  });
});
