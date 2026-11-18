import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { morphName } from "@/components/patterns/morph/MorphPart";
import type { Lobby } from "@/features/community/domain/lobby";
import { CommunityLobby } from "@/features/community/ui/lobby/CommunityLobby";
import { useUsers } from "@/features/identity/api/use-users";
import { jsonResponse, urlOf } from "@/test/fetch";
import { lobbyFixture, lobbyItem } from "@/test/lobby-fixture";
import { inQueryClient } from "@/test/query-client";

vi.mock("@/components/patterns/morph/MorphPart", async (actual) => {
  const real =
    await actual<typeof import("@/components/patterns/morph/MorphPart")>();
  return {
    ...real,
    MorphPart: ({
      part,
      id,
      children,
    }: {
      part: Parameters<typeof real.morphName>[0];
      id: string | undefined;
      children: ReactElement;
    }) => (
      <span data-morph={id === undefined ? "" : real.morphName(part, id)}>
        {children}
      </span>
    ),
  };
});
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const shared = lobbyItem({ id: 7, slug: "kafka-circle", name: "Kafka Circle" });
const BANNER = morphName("banner", "community-kafka-circle");
const originalScrollTo = Element.prototype.scrollTo;

function stubLobby(lobby: Lobby) {
  vi.spyOn(globalThis, "fetch").mockImplementation((input) => {
    const { pathname } = new URL(urlOf(input), "http://localhost");
    return Promise.resolve(
      pathname === "/axonia/lobby"
        ? jsonResponse(lobby)
        : jsonResponse({ status: 404 }, 404),
    );
  });
}

async function renderLobby(lobby: Lobby) {
  stubLobby(lobby);
  render(
    <MemoryRouter>
      <CommunityLobby canCreate={false} />
    </MemoryRouter>,
    inQueryClient(),
  );
  await screen.findAllByRole("button", { name: "Kafka Circle" });
}

/** Every morph name worn on the page, the dialog's portal included. */
function wornNames(): string[] {
  return [...document.querySelectorAll("[data-morph]")]
    .map((element) => element.getAttribute("data-morph") ?? "")
    .filter((name) => name !== "");
}

function wearer(name: string): Element | null {
  return document.querySelector(`[data-morph="${name}"]`);
}

beforeEach(() => {
  Element.prototype.scrollTo = vi.fn();
  vi.mocked(useUsers).mockReturnValue({ data: undefined } as never);
});

afterEach(() => {
  Element.prototype.scrollTo = originalScrollTo;
  vi.restoreAllMocks();
});

describe("the lobby's morph names", () => {
  const everywhere = lobbyFixture({
    billboard: [shared, lobbyItem({ id: 8 })],
    shelves: [
      { key: "JOINED", items: [lobbyItem({ id: 9 })], hasMore: false },
      { key: "THIS_WEEK", items: [shared], hasMore: false },
      {
        key: "GROWING",
        items: [lobbyItem({ id: 10 }), shared],
        hasMore: false,
      },
    ],
  });

  it("are worn once, by the slide on show, when the billboard and two shelves show one community", async () => {
    // when
    await renderLobby(everywhere);

    // then
    const names = wornNames();
    expect(names).toEqual([...new Set(names)]);
    expect(names).toContain(BANNER);
    expect(
      wearer(BANNER)
        ?.closest('[aria-roledescription="slide"]')
        ?.getAttribute("aria-label"),
    ).toBe("Kafka Circle, 1 of 2");
  });

  it("move to the open threshold, and are still worn once", async () => {
    // given
    await renderLobby(everywhere);
    const shelf = screen.getByRole("region", { name: "Growing fast" });

    // when
    await userEvent.click(
      within(shelf).getByRole("button", { name: "Kafka Circle" }),
    );
    await screen.findByRole("dialog", { name: "Kafka Circle" });

    // then
    const names = wornNames();
    expect(names).toEqual([...new Set(names)]);
    expect(wearer(BANNER)?.closest('[role="dialog"]')).not.toBeNull();
  });

  it("fall to the first card in render order when the billboard does not show the community", async () => {
    // when
    await renderLobby(
      lobbyFixture({
        billboard: [lobbyItem({ id: 8 })],
        shelves: [
          { key: "THIS_WEEK", items: [shared], hasMore: false },
          { key: "GROWING", items: [shared], hasMore: false },
        ],
      }),
    );

    // then
    const names = wornNames();
    expect(names).toEqual([...new Set(names)]);
    expect(
      wearer(BANNER)?.closest("section")?.getAttribute("aria-labelledby"),
    ).toBe(
      screen
        .getByRole("heading", { name: "Sessions this week" })
        .getAttribute("id"),
    );
  });
});
