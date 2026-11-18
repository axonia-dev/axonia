import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useJoin } from "@/features/community/api/use-membership";
import type { LobbyItem } from "@/features/community/domain/lobby";
import {
  LobbyBillboardSlide,
  LobbyCreateHero,
} from "@/features/community/ui/lobby/LobbyBillboardSlide";
import { lobbyItem } from "@/test/lobby-fixture";
import { inQueryClient } from "@/test/query-client";

vi.mock("@/features/community/api/use-membership", () => ({
  useJoin: vi.fn(),
}));

const mockUseJoin = vi.mocked(useJoin);
const faces = vi.fn(() => []);

function renderSlide(item: LobbyItem, onOpenThreshold = vi.fn()) {
  return render(
    <MemoryRouter>
      <LobbyBillboardSlide
        item={item}
        active
        faces={faces}
        onOpenThreshold={onOpenThreshold}
      />
    </MemoryRouter>,
    inQueryClient(),
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseJoin.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
  } as never);
});

describe("LobbyBillboardSlide", () => {
  it("features an unjoined community with Join and Peek inside", () => {
    // when
    renderSlide(lobbyItem({ name: "Backend Guild", joinedLast30Days: 9 }));

    // then
    expect(
      screen.getByRole("heading", { name: "Backend Guild" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Guild · +9 joined in 30 days"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Join" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Peek inside" }),
    ).toBeInTheDocument();
  });

  it("offers only Open for a joined community, with no Join and no peek", () => {
    // when
    renderSlide(lobbyItem({ role: "MEMBER" }));

    // then
    expect(screen.getByRole("link", { name: "Open" })).toHaveAttribute(
      "href",
      "/communities/community-1",
    );
    expect(screen.queryByRole("button", { name: "Join" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Peek inside" })).toBeNull();
  });

  it("opens the threshold from Peek inside", async () => {
    // given
    const onOpenThreshold = vi.fn();
    const item = lobbyItem();
    renderSlide(item, onOpenThreshold);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Peek inside" }));

    // then
    expect(onOpenThreshold).toHaveBeenCalledWith(item);
  });

  it("states an empty community's facts without a missing value", () => {
    // when
    const { container } = renderSlide(lobbyItem());

    // then
    expect(screen.getByText("Nothing scheduled yet")).toBeInTheDocument();
    expect(screen.getByText("1 member")).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/null|undefined/);
  });

  it("marks a session this week with the live dot", () => {
    // when
    renderSlide(
      lobbyItem({
        nextSessionThisWeek: true,
        nextSession: {
          title: "Kafka",
          scheduledAt: "2026-11-19T19:00:00Z",
          hasTime: true,
          zone: "Europe/Zurich",
        },
      }),
    );

    // then
    expect(
      screen.getByRole("img", { name: "Session this week" }),
    ).toBeInTheDocument();
  });

  it("clamps the description under the name", () => {
    // when
    renderSlide(lobbyItem({ description: "Where services meet." }));

    // then
    expect(screen.getByText("Where services meet.")).toHaveClass(
      "line-clamp-2",
    );
  });
});

describe("LobbyCreateHero", () => {
  it("invites founding the first community", () => {
    // when
    render(
      <MemoryRouter>
        <LobbyCreateHero canCreate />
      </MemoryRouter>,
      inQueryClient(),
    );

    // then
    expect(
      screen.getByRole("heading", { name: "Start a community here" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Create a community" }),
    ).toHaveAttribute("href", "/communities/new");
  });

  it("offers no create link to someone who cannot create", () => {
    // when
    render(
      <MemoryRouter>
        <LobbyCreateHero canCreate={false} />
      </MemoryRouter>,
      inQueryClient(),
    );

    // then
    expect(
      screen.getByRole("heading", { name: "No communities yet" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Be the first")).toBeNull();
    expect(
      screen.queryByRole("link", { name: "Create a community" }),
    ).toBeNull();
  });
});
