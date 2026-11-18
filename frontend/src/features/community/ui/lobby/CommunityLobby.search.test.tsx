import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

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

const directory = new Map([
  ["zoe", { displayName: "Zoe Zimmer", avatarUrl: "/zoe.png" }],
]);

const match = lobbyItem({ id: 9, name: "Kafka Guild", faces: ["zoe"] });

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useUsers).mockImplementation(
    (ids: string[]) =>
      ({
        data: Object.fromEntries(
          ids.flatMap((id) => {
            const user = directory.get(id);
            return user === undefined ? [] : [[id, user]];
          }),
        ),
      }) as never,
  );
  vi.mocked(useJoin).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useLobby).mockReturnValue({
    data: lobbyFixture({
      shelves: [
        {
          key: "EXPLORE",
          items: [lobbyItem({ id: 1, name: "Data Guild" })],
          hasMore: true,
        },
      ],
    }),
    errorUpdateCount: 0,
    isFetching: false,
    refetch: vi.fn(),
  } as never);
  vi.mocked(useLobbyShelf).mockImplementation(
    (_key, firstPage: LobbyItem[]) =>
      ({
        data: { pages: [{ items: firstPage, hasMore: true }], pageParams: [0] },
        hasNextPage: true,
        isFetchingNextPage: false,
        isFetchNextPageError: false,
        fetchNextPage: vi.fn(),
      }) as never,
  );
  vi.mocked(useLobbySearch).mockImplementation(
    (q: string) =>
      ({
        data:
          q.trim() === ""
            ? undefined
            : { pages: [{ content: [match], totalElements: 1 }] },
        errorUpdateCount: 0,
        hasNextPage: false,
        isFetchingNextPage: false,
        isFetchNextPageError: false,
        fetchNextPage: vi.fn(),
      }) as never,
  );
});

describe("a threshold opened from a search result", () => {
  it("names the people inside, though the lobby read never carried them", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    render(
      <MemoryRouter>
        <CommunityLobby canCreate={false} />
      </MemoryRouter>,
      inQueryClient(),
    );
    await user.type(
      screen.getByRole("searchbox", { name: "Search communities" }),
      "kafka",
    );

    // when
    await user.click(screen.getByRole("button", { name: "Kafka Guild" }));

    // then
    const who = within(screen.getByRole("dialog", { name: "Kafka Guild" }))
      .getByRole("heading", { name: "Who is here" })
      .closest("section");
    if (!(who instanceof HTMLElement)) throw new Error("no crowd");
    expect(
      within(who).getByRole("img", { name: "Zoe Zimmer" }),
    ).toBeInTheDocument();
  });
});
