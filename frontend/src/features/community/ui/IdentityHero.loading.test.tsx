import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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
vi.mock(
  "@/features/community/ui/ceremony/HeroCeremony",
  () => new Promise(() => undefined),
);
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

function card(): HTMLElement {
  return screen.getByLabelText("Community identity", { selector: "section" });
}

beforeEach(() => {
  vi.mocked(useMyMembership).mockReturnValue({
    data: null,
    isError: false,
  } as never);
  vi.mocked(useMembers).mockReturnValue({ data: { content: [] } } as never);
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
  vi.mocked(useManagementAccess).mockReturnValue({ invite: false } as never);
  handOffCeremony("birth", "backend-guild");
});

afterEach(() => {
  vi.restoreAllMocks();
});

function preferReducedMotion() {
  vi.spyOn(window, "matchMedia").mockImplementation(
    (query) =>
      ({
        matches: query.includes("reduce"),
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
      }) as unknown as MediaQueryList,
  );
}

describe("IdentityHero while the ceremony loads", () => {
  it("holds up the dark stage at once and hides the page's own card", () => {
    // when
    renderHero();

    // then
    expect(document.querySelector("[data-ceremony-backdrop]")).not.toBeNull();
    expect(card().parentElement).toHaveClass("invisible");
  });

  it("never hides the card under reduced motion", () => {
    // given
    preferReducedMotion();

    // when
    renderHero();

    // then
    expect(document.querySelector("[data-ceremony-backdrop]")).toBeNull();
    expect(card().parentElement).not.toHaveClass("invisible");
  });
});
