import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useJoin } from "@/features/community/api/use-membership";
import type { LobbyItem } from "@/features/community/domain/lobby";
import { LobbyCard } from "@/features/community/ui/lobby/LobbyCard";
import { lobbyItem } from "@/test/lobby-fixture";
import { inQueryClient } from "@/test/query-client";

vi.mock("@/features/community/api/use-membership", () => ({
  useJoin: vi.fn(),
}));

const mockUseJoin = vi.mocked(useJoin);
const join = vi.fn();
const faces = vi.fn(() => [
  { id: "alice", name: "Alice", hue: "#6e3ceb", src: null },
]);

let user: ReturnType<typeof userEvent.setup>;

function renderCard(item: LobbyItem, onOpenThreshold = vi.fn()) {
  render(
    <MemoryRouter initialEntries={["/communities"]}>
      <Routes>
        <Route
          path="/communities"
          element={
            <LobbyCard
              item={item}
              faces={faces}
              onOpenThreshold={onOpenThreshold}
              showRole
            />
          }
        />
        <Route path="/communities/:slug" element={<h1>Inside</h1>} />
      </Routes>
    </MemoryRouter>,
    inQueryClient(),
  );
  return { card: screen.getByRole("article", { name: item.name }) };
}

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  mockUseJoin.mockReturnValue({
    mutate: join,
    isPending: false,
    isError: false,
  } as never);
});

describe("LobbyCard", () => {
  it("shows its facts and Join without being approached", () => {
    // when
    const { card } = renderCard(
      lobbyItem({ kind: "Community of Practice", memberCount: 3 }),
    );

    // then
    expect(card).toHaveTextContent("Community of Practice");
    expect(card).toHaveTextContent("3 members");
    expect(within(card).getByRole("button", { name: "Join" })).toBeVisible();
  });

  it("opens the threshold of an unjoined community on a click", async () => {
    // given
    const onOpenThreshold = vi.fn();
    const item = lobbyItem();
    renderCard(item, onOpenThreshold);

    // when
    await user.click(screen.getByRole("button", { name: item.name }));

    // then
    expect(onOpenThreshold).toHaveBeenCalledWith(item);
  });

  it("joins from Join without opening the threshold", async () => {
    // given
    const onOpenThreshold = vi.fn();
    renderCard(lobbyItem(), onOpenThreshold);

    // when
    await user.click(screen.getByRole("button", { name: "Join" }));

    // then
    expect(join).toHaveBeenCalled();
    expect(onOpenThreshold).not.toHaveBeenCalled();
  });

  it("opens a joined community in one click, with no threshold and no Join", async () => {
    // given
    const onOpenThreshold = vi.fn();
    const item = lobbyItem({ role: "MEMBER", slug: "kafka-circle" });
    renderCard(item, onOpenThreshold);
    const open = screen.getByRole("link", { name: item.name });
    expect(open).toHaveAttribute("href", "/communities/kafka-circle");
    expect(screen.queryByRole("button", { name: "Join" })).toBeNull();

    // when
    await user.click(open);

    // then
    expect(
      await screen.findByRole("heading", { name: "Inside" }),
    ).toBeInTheDocument();
    expect(onOpenThreshold).not.toHaveBeenCalled();
  });

  it("renders an empty community's facts without a missing value", () => {
    // when
    const { card } = renderCard(lobbyItem());

    // then
    expect(card).toHaveTextContent("Nothing scheduled yet");
    expect(card).toHaveTextContent("1 member");
    expect(card.textContent).not.toMatch(/null|undefined/);
  });

  it("wears the role chip and the live dot on a joined community meeting this week", () => {
    // when
    renderCard(lobbyItem({ role: "ORGANIZER", nextSessionThisWeek: true }));

    // then
    expect(screen.getByText("Organizer")).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "Session this week" }),
    ).toBeInTheDocument();
  });

  it("draws the hero at its focal point", () => {
    // when
    renderCard(
      lobbyItem({ heroUrl: "/hero.png", heroFocus: { x: 20, y: 70 } }),
    );

    // then
    const hero = document.querySelector("img");
    expect(hero).toHaveAttribute("src", "/hero.png");
    expect(hero?.style.objectPosition).toBe("20% 70%");
  });
});
