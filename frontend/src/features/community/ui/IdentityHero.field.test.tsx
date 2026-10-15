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
    onDone,
  }: {
    ceremony: Record<"kind" | "countFrom" | "countTo" | "placement", string>;
    onDone: () => void;
  }) => (
    <div data-placement={ceremony.placement}>
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
vi.mock("@/features/community/ui/HeroAmbientField", () => ({
  HeroAmbientField: () => <div data-testid="ambient-field" />,
}));

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

describe("IdentityHero's ambient field", () => {
  it("draws the field behind the hero", () => {
    // when
    renderHero();

    // then
    expect(screen.getByTestId("ambient-field")).toBeInTheDocument();
  });

  it("draws no field while a ceremony plays, and draws it once the ceremony is done", async () => {
    // given
    handOffCeremony("birth", "backend-guild");

    // when
    renderHero();

    // then
    await screen.findByText("ceremony birth 42 to 42");
    expect(screen.queryByTestId("ambient-field")).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "end ceremony" }));
    expect(await screen.findByTestId("ambient-field")).toBeInTheDocument();
  });

  it("renders the field before the identity, whose content sits on layer 2", () => {
    // when
    renderHero();

    // then
    const field = screen.getByTestId("ambient-field");
    const identity = document.querySelector('[data-ceremony-part="identity"]')!;
    expect(
      field.compareDocumentPosition(identity) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(identity.firstElementChild).toHaveClass("z-[2]");
  });
});
