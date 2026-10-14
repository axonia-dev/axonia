import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { handOffCeremony } from "@/features/community/api/ceremony-handoff";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useMembers,
  useMyMembership,
} from "@/features/community/api/use-membership";
import type { Community } from "@/features/community/domain/community";
import { IdentityHero } from "@/features/community/ui/IdentityHero";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/features/community/ui/JoinAction", () => ({
  JoinAction: ({ onJoined }: { onJoined?: () => void }) => (
    <button onClick={onJoined}>Join</button>
  ),
}));
vi.mock("@/features/community/ui/CommunityMenu", () => ({
  CommunityMenu: () => <button>community-menu</button>,
}));
vi.mock("@/features/community/ui/ceremony/HeroCeremony", () => ({
  HeroCeremony: ({
    ceremony,
    placement,
    onDone,
  }: {
    ceremony: { kind: string; countFrom: number; countTo: number };
    placement?: string;
    onDone: () => void;
  }) => (
    <div data-placement={placement}>
      ceremony {ceremony.kind} {ceremony.countFrom} to {ceremony.countTo}
      <button onClick={onDone}>end ceremony</button>
    </div>
  ),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useMyMembership: vi.fn(),
  useMembers: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: () => ({ data: { kind: "ENTERPRISE" } }),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const community: Community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "",
  status: "ACTIVE",
  memberCount: 42,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function renderHero() {
  return render(
    <MemoryRouter>
      <IdentityHero community={community} slug="backend-guild" />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(useMyMembership).mockReturnValue({
    data: null,
    isError: false,
  } as never);
  vi.mocked(useMembers).mockReturnValue({ data: { content: [] } } as never);
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
  vi.mocked(useManagementAccess).mockReturnValue({ invite: false } as never);
});

describe("IdentityHero ceremonies", () => {
  it("plays nothing on an ordinary visit", () => {
    // when
    renderHero();

    // then
    expect(screen.queryByText(/ceremony/)).toBeNull();
  });

  it("welcomes a member right after they press Join", async () => {
    // given
    renderHero();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // then
    expect(
      await screen.findByText("ceremony welcome 42 to 43"),
    ).toBeInTheDocument();
  });

  it("plays a ceremony handed off to this community", async () => {
    // given
    handOffCeremony("birth", "backend-guild");

    // when
    renderHero();

    // then
    expect(
      await screen.findByText("ceremony birth 42 to 42"),
    ).toBeInTheDocument();
  });

  it("announces the welcome politely", async () => {
    // given
    renderHero();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // then
    expect(screen.getByRole("status")).toHaveTextContent(
      "Joined Backend Guild",
    );
  });

  it("plays a join made on the page inside the card, with no backdrop", async () => {
    // given
    renderHero();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // then
    const stage = await screen.findByText("ceremony welcome 42 to 43");
    expect(stage).toHaveAttribute("data-placement", "in-place");
    expect(document.querySelector("[data-ceremony-backdrop]")).toBeNull();
  });

  it("plays a handed-off welcome on the full-screen backdrop", async () => {
    // given
    handOffCeremony("welcome", "backend-guild");

    // when
    renderHero();

    // then
    const stage = await screen.findByText("ceremony welcome 41 to 42");
    expect(stage).toHaveAttribute("data-placement", "viewport");
    expect(document.querySelector("[data-ceremony-backdrop]")).not.toBeNull();
  });

  it("rounds the card's frame like the card, so an in-place stage clips to its corners", () => {
    // when
    renderHero();

    // then
    const card = screen.getByLabelText("Community identity");
    expect(card.parentElement).toHaveClass("rounded-lg");
  });

  it("hides the page's own card while the ceremony plays", async () => {
    // given
    renderHero();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // then
    const card = screen.getByLabelText("Community identity");
    expect(card.parentElement).toHaveClass("invisible");
  });

  it("returns focus to the footer once the welcome has played", async () => {
    // given
    renderHero();
    await userEvent.click(screen.getByRole("button", { name: "Join" }));
    vi.mocked(useMyMembership).mockReturnValue({
      data: { role: "MEMBER" },
      isError: false,
    } as never);

    // when
    await userEvent.click(
      await screen.findByRole("button", { name: "end ceremony" }),
    );

    // then
    expect(
      screen.getByRole("button", { name: "community-menu" }),
    ).toHaveFocus();
  });

  it("returns focus to the footer when the membership answer lands after the ceremony", async () => {
    // given the ceremony is over (skipped early, or never staged under reduced motion)
    const { rerender } = renderHero();
    await userEvent.click(screen.getByRole("button", { name: "Join" }));
    await userEvent.click(
      await screen.findByRole("button", { name: "end ceremony" }),
    );

    // when
    vi.mocked(useMyMembership).mockReturnValue({
      data: { role: "MEMBER" },
      isError: false,
    } as never);
    rerender(
      <MemoryRouter>
        <IdentityHero community={community} slug="backend-guild" />
      </MemoryRouter>,
    );

    // then
    expect(
      screen.getByRole("button", { name: "community-menu" }),
    ).toHaveFocus();
  });

  it("announces a rejoin again, with a fresh live region", async () => {
    // given
    renderHero();
    await userEvent.click(screen.getByRole("button", { name: "Join" }));
    await userEvent.click(
      await screen.findByRole("button", { name: "end ceremony" }),
    );
    const first = screen.getByRole("status");

    // when
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // then
    const actual = screen.getByRole("status");
    expect(actual).not.toBe(first);
    expect(actual).toHaveTextContent("Joined Backend Guild");
  });
});
