import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { LobbySearchGrid } from "@/features/community/ui/lobby/LobbyGrid";
import { useUsers } from "@/features/identity/api/use-users";
import { jsonResponse, urlOf } from "@/test/fetch";
import { lobbyItem } from "@/test/lobby-fixture";
import { inQueryClient } from "@/test/query-client";

vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const originalObserver = window.IntersectionObserver;
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
  disconnect() {
    reach = undefined;
  }
}

function stubSearch() {
  const pageOne = vi.fn(() => jsonResponse({ status: 500 }, 500));
  vi.spyOn(globalThis, "fetch").mockImplementation((input) => {
    const url = new URL(urlOf(input), "http://localhost");
    if (url.searchParams.get("page") === "1") return Promise.resolve(pageOne());
    return Promise.resolve(
      jsonResponse({
        content: [lobbyItem({ id: 1 })],
        page: { size: 20, number: 0, totalElements: 40, totalPages: 2 },
      }),
    );
  });
  return pageOne;
}

beforeEach(() => {
  reach = undefined;
  window.IntersectionObserver =
    ReachableObserver as unknown as typeof IntersectionObserver;
  vi.mocked(useUsers).mockReturnValue({ data: undefined } as never);
});

afterEach(() => {
  window.IntersectionObserver = originalObserver;
  vi.restoreAllMocks();
});

describe("loading more after a page fails", () => {
  it("stops asking, says so, and asks again only when told to", async () => {
    // given
    const pageOne = stubSearch();
    render(
      <MemoryRouter>
        <LobbySearchGrid q="guild" onOpenThreshold={vi.fn()} />
      </MemoryRouter>,
      inQueryClient(),
    );
    await screen.findByRole("button", { name: "Community 1" });

    // when
    act(() => reach?.());
    await screen.findByText("Could not load more communities.");

    // then
    expect(reach).toBeUndefined();
    expect(pageOne).toHaveBeenCalledOnce();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    await waitFor(() => expect(pageOne).toHaveBeenCalledTimes(2));
  });
});
