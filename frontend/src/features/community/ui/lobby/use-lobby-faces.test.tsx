import { renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useLobbyFaces } from "@/features/community/ui/lobby/use-lobby-faces";
import { useUsers } from "@/features/identity/api/use-users";
import { userHue } from "@/lib/user-hue";
import { lobbyItem } from "@/test/lobby-fixture";

vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const mockUseUsers = vi.mocked(useUsers);

function user(id: string, displayName: string) {
  return {
    id,
    displayName,
    avatarUrl: `/${id}.png`,
    memberStatus: "PRESENT",
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseUsers.mockReturnValue({
    data: { alice: user("alice", "Alice"), bob: user("bob", "Bob") },
  } as never);
});

describe("useLobbyFaces", () => {
  it("resolves every item's faces through one read of the union of their ids", () => {
    // given
    const items = [
      lobbyItem({ id: 1, faces: ["alice", "bob"] }),
      lobbyItem({ id: 2, faces: ["bob"] }),
    ];

    // when
    renderHook(() => useLobbyFaces(items));

    // then
    expect(mockUseUsers).toHaveBeenCalledTimes(1);
    expect(mockUseUsers).toHaveBeenCalledWith(["alice", "bob"]);
  });

  it("names, colours and draws each face, capped", () => {
    // given
    const item = lobbyItem({ faces: ["alice", "bob"] });
    const { result } = renderHook(() => useLobbyFaces([item]));

    // when
    const actual = result.current(item, 1);

    // then
    expect(actual).toEqual([
      {
        id: "alice",
        name: "Alice",
        hue: userHue("alice"),
        src: "/alice.png",
      },
    ]);
  });

  it("names a face the directory has not answered yet as someone", () => {
    // given
    mockUseUsers.mockReturnValue({ data: undefined } as never);
    const item = lobbyItem({ faces: ["carol"] });
    const { result } = renderHook(() => useLobbyFaces([item]));

    // when
    const actual = result.current(item, 5);

    // then
    expect(actual).toEqual([
      { id: "carol", name: "Someone", hue: userHue("carol"), src: null },
    ]);
  });

  it("answers an item it was not given through its fallback", () => {
    // given
    const fallback = vi.fn(() => []);
    const own = lobbyItem({ id: 1 });
    const foreign = lobbyItem({ id: 2 });
    const { result } = renderHook(() => useLobbyFaces([own], fallback));

    // when
    result.current(foreign, 4);

    // then
    expect(fallback).toHaveBeenCalledWith(foreign, 4);
  });
});
