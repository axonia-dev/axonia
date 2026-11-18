import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useJoin } from "@/features/community/api/use-membership";
import type { LobbyItem } from "@/features/community/domain/lobby";
import { LobbyThreshold } from "@/features/community/ui/lobby/LobbyThreshold";
import { useUsers } from "@/features/identity/api/use-users";
import { lobbyItem } from "@/test/lobby-fixture";
import { inQueryClient } from "@/test/query-client";

vi.mock("@/features/community/api/use-membership", () => ({
  useJoin: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const mockUseJoin = vi.mocked(useJoin);
const mutate = vi.fn();
const onClose = vi.fn();

let user: ReturnType<typeof userEvent.setup>;

function mockJoin(state: Record<string, unknown> = {}) {
  mockUseJoin.mockReturnValue({
    mutate,
    isPending: false,
    isError: false,
    ...state,
  } as never);
}

function renderThreshold(item: LobbyItem | null) {
  render(
    <MemoryRouter>
      <LobbyThreshold item={item} onClose={onClose} />
    </MemoryRouter>,
    inQueryClient(),
  );
}

function dialog(name = "Backend Guild") {
  return screen.getByRole("dialog", { name });
}

beforeEach(() => {
  vi.mocked(useUsers).mockImplementation(
    (ids: string[]) =>
      ({
        data: Object.fromEntries(
          ids.map((id) => [id, { displayName: id, avatarUrl: null }]),
        ),
      }) as never,
  );
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  mockJoin();
});

describe("LobbyThreshold", () => {
  it("opens as a dialog named after the community", () => {
    // when
    renderThreshold(lobbyItem({ name: "Backend Guild" }));

    // then
    expect(dialog()).toBeInTheDocument();
  });

  it("renders nothing while no community is chosen", () => {
    // when
    renderThreshold(null);

    // then
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("closes on Escape", async () => {
    // given
    renderThreshold(lobbyItem({ name: "Backend Guild" }));

    // when
    await user.keyboard("{Escape}");

    // then
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("closes from its close button", async () => {
    // given
    renderThreshold(lobbyItem({ name: "Backend Guild" }));

    // when
    await user.click(screen.getByRole("button", { name: "Close" }));

    // then
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("joins from the threshold and offers to peek inside first", async () => {
    // given
    renderThreshold(lobbyItem({ name: "Backend Guild" }));

    // when
    await user.click(within(dialog()).getByRole("button", { name: "Join" }));

    // then
    expect(mutate).toHaveBeenCalledOnce();
    expect(
      within(dialog()).getByRole("link", { name: "Peek inside" }),
    ).toHaveAttribute("href", "/communities/community-1");
  });

  it("holds Join while the join is under way", async () => {
    // given
    mockJoin({ isPending: true });
    renderThreshold(lobbyItem({ name: "Backend Guild" }));
    const join = within(dialog()).getByRole("button", { name: "Join" });

    // when
    await user.click(join);

    // then
    expect(mutate).not.toHaveBeenCalled();
    expect(join.querySelector("[data-button-spinner]")).not.toBeNull();
  });

  it("offers Open and no peek to a member", () => {
    // when
    renderThreshold(lobbyItem({ name: "Backend Guild", role: "MEMBER" }));

    // then
    expect(within(dialog()).getByRole("link", { name: "Open" })).toBeVisible();
    expect(within(dialog()).queryByRole("button", { name: "Join" })).toBeNull();
    expect(
      within(dialog()).queryByRole("link", { name: "Peek inside" }),
    ).toBeNull();
  });

  it("counts members, sessions held and things shared", () => {
    // when
    renderThreshold(
      lobbyItem({
        name: "Backend Guild",
        memberCount: 20,
        stats: { sessionsHeld: 1, artifactsShared: 4 },
      }),
    );

    // then
    expect(dialog()).toHaveTextContent("20members");
    expect(dialog()).toHaveTextContent("1session held");
    expect(dialog()).toHaveTextContent("4things shared");
  });

  it("leaves out Lately inside for a community where nothing happened yet", () => {
    // when
    renderThreshold(lobbyItem({ name: "Backend Guild" }));

    // then
    expect(within(dialog()).queryByText("Lately inside")).toBeNull();
    expect(within(dialog()).getByText("Nothing scheduled yet")).toBeVisible();
    expect(dialog().textContent).not.toMatch(/null|undefined/);
  });

  it("names only what happened lately", () => {
    // when
    renderThreshold(
      lobbyItem({
        name: "Backend Guild",
        lately: { artifactTitle: "Outbox pattern", topicTitle: null },
      }),
    );

    // then
    expect(within(dialog()).getByText("Outbox pattern")).toBeVisible();
    expect(within(dialog()).queryByText("Topic proposed")).toBeNull();
    expect(within(dialog()).queryByText(/^Held /)).toBeNull();
  });

  it("names the latest held session and the latest proposed topic", () => {
    // when
    renderThreshold(
      lobbyItem({
        name: "Backend Guild",
        latestSession: {
          title: null,
          scheduledAt: "2026-11-02T23:00:00Z",
          hasTime: false,
          zone: "Europe/Zurich",
        },
        lately: { artifactTitle: null, topicTitle: "Event sourcing" },
      }),
    );

    // then
    expect(within(dialog()).getByText("A session")).toBeVisible();
    expect(within(dialog()).getByText("Held Tue 3 Nov")).toBeVisible();
    expect(within(dialog()).getByText("Event sourcing")).toBeVisible();
  });

  it("states the next session", () => {
    // when
    renderThreshold(
      lobbyItem({
        name: "Backend Guild",
        nextSessionThisWeek: true,
        nextSession: {
          title: "Kafka deep dive",
          scheduledAt: "2026-11-19T19:00:00Z",
          hasTime: true,
          zone: "Europe/Zurich",
        },
      }),
    );

    // then
    expect(within(dialog()).getByText("Kafka deep dive")).toBeVisible();
    expect(within(dialog()).getByText("Thu 19 Nov · 11:00")).toBeVisible();
  });

  it("shows twelve faces and counts the rest", () => {
    // when
    renderThreshold(
      lobbyItem({
        name: "Backend Guild",
        memberCount: 20,
        faces: Array.from({ length: 12 }, (_, i) => `member-${i}`),
      }),
    );

    // then
    expect(within(dialog()).getByText("+8")).toBeVisible();
  });

  it("counts no rest when every member has a face", () => {
    // when
    renderThreshold(
      lobbyItem({ name: "Backend Guild", memberCount: 1, faces: ["alice"] }),
    );

    // then
    expect(within(dialog()).queryByText(/^\+\d+$/)).toBeNull();
  });
});
