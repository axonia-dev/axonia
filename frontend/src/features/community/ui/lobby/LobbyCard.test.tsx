import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useJoin } from "@/features/community/api/use-membership";
import type { LobbyItem } from "@/features/community/domain/lobby";
import { LobbyCard } from "@/features/community/ui/lobby/LobbyCard";
import { lobbyItem } from "@/test/lobby-fixture";

vi.mock("@/features/community/api/use-membership", () => ({
  useJoin: vi.fn(),
}));

const mockUseJoin = vi.mocked(useJoin);
const faces = vi.fn(() => [
  { id: "alice", name: "Alice", hue: "#6e3ceb", src: null },
]);

let user: ReturnType<typeof userEvent.setup>;

function renderCard(
  item: LobbyItem,
  onOpenThreshold = vi.fn(),
  preview = true,
) {
  render(
    <MemoryRouter>
      <LobbyCard
        item={item}
        faces={faces}
        onOpenThreshold={onOpenThreshold}
        showRole
        preview={preview}
      />
      <button type="button">Elsewhere</button>
    </MemoryRouter>,
  );
  const face = screen.getByRole("button", { name: item.name });
  const card = face.closest("[data-preview]");
  const panel = card?.querySelector<HTMLElement>("[data-preview-panel]");
  if (!(card instanceof HTMLElement) || panel == null) {
    throw new Error("no card");
  }
  return { face, card, panel };
}

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  mockUseJoin.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
});

describe("LobbyCard", () => {
  it("keeps the preview closed and out of reach until the card is approached", () => {
    // when
    const { card, panel } = renderCard(lobbyItem());

    // then
    expect(card).toHaveAttribute("data-preview", "closed");
    expect(panel).toHaveAttribute("inert");
  });

  it("opens the preview with Join when an unjoined card takes focus", async () => {
    // given
    const { card, panel } = renderCard(lobbyItem());

    // when
    await user.tab();

    // then
    expect(card).toHaveAttribute("data-preview", "open");
    expect(panel).not.toHaveAttribute("inert");
    expect(
      within(panel).getByRole("button", { name: "Join" }),
    ).toBeInTheDocument();
  });

  it("offers no Join in the preview of a joined community", async () => {
    // given
    const { panel } = renderCard(lobbyItem({ role: "MEMBER" }));

    // when
    await user.tab();

    // then
    expect(within(panel).queryByRole("button", { name: "Join" })).toBeNull();
  });

  it("opens the preview under a mouse", () => {
    // given
    const { card } = renderCard(lobbyItem());

    // when
    fireEvent.pointerEnter(card, { pointerType: "mouse" });

    // then
    expect(card).toHaveAttribute("data-preview", "open");
  });

  it("opens no preview under a finger, which has no hover", () => {
    // given
    const { card } = renderCard(lobbyItem());

    // when
    fireEvent.pointerEnter(card, { pointerType: "touch" });

    // then
    expect(card).toHaveAttribute("data-preview", "closed");
  });

  it("closes the preview when the mouse leaves", () => {
    // given
    const { card } = renderCard(lobbyItem());
    fireEvent.pointerEnter(card, { pointerType: "mouse" });

    // when
    fireEvent.pointerLeave(card, { pointerType: "mouse" });

    // then
    expect(card).toHaveAttribute("data-preview", "closed");
  });

  it("closes the preview when focus leaves the card", async () => {
    // given
    const { card } = renderCard(lobbyItem({ role: "MEMBER" }));
    await user.tab();

    // when
    await user.tab();

    // then
    expect(card).toHaveAttribute("data-preview", "closed");
  });

  it("opens no preview on the focus a tap gives", () => {
    // given
    const { card, face } = renderCard(lobbyItem());

    // when
    fireEvent.pointerDown(card, { pointerType: "touch" });
    fireEvent.focus(face);

    // then
    expect(card).toHaveAttribute("data-preview", "closed");
  });

  it("opens no preview when a closed threshold hands focus back after a tap", () => {
    // given
    const { card, face } = renderCard(lobbyItem());
    fireEvent.pointerDown(face, { pointerType: "touch" });
    fireEvent.pointerUp(face, { pointerType: "touch" });

    // when
    act(() => face.focus());

    // then
    expect(card).toHaveAttribute("data-preview", "closed");
  });

  it("opens no preview when focus returns after a mouse click elsewhere", async () => {
    // given
    const { card, face } = renderCard(lobbyItem());
    await user.click(screen.getByRole("button", { name: "Elsewhere" }));

    // when
    act(() => face.focus());

    // then
    expect(card).toHaveAttribute("data-preview", "closed");
  });

  it("opens no preview where it is turned off, under focus or a mouse", async () => {
    // given
    const { card } = renderCard(lobbyItem(), vi.fn(), false);

    // when
    await user.tab();
    fireEvent.pointerEnter(card, { pointerType: "mouse" });

    // then
    expect(card).toHaveAttribute("data-preview", "closed");
  });

  it("opens the threshold on a click", async () => {
    // given
    const onOpenThreshold = vi.fn();
    const item = lobbyItem();
    const { face } = renderCard(item, onOpenThreshold);

    // when
    await user.click(face);

    // then
    expect(onOpenThreshold).toHaveBeenCalledWith(item);
  });

  it("renders an empty community's facts without a missing value", () => {
    // when
    const { panel } = renderCard(lobbyItem());

    // then
    expect(panel).toHaveTextContent("Nothing scheduled yet");
    expect(panel).toHaveTextContent("1 member");
    expect(panel.textContent).not.toMatch(/null|undefined/);
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
