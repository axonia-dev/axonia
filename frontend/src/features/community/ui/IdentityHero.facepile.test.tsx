import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useMembers,
  useMyMembership,
} from "@/features/community/api/use-membership";
import type { Community } from "@/features/community/domain/community";
import { IdentityHero } from "@/features/community/ui/IdentityHero";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/features/community/ui/JoinAction", () => ({
  JoinAction: () => <div>join-action</div>,
}));
vi.mock("@/features/community/ui/MembershipChip", () => ({
  MembershipChip: () => <div>membership-chip</div>,
}));
vi.mock("@/features/community/ui/CommunityMenu", () => ({
  CommunityMenu: () => <div>community-menu</div>,
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useMyMembership: vi.fn(),
  useMembers: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(() => ({ data: { kind: "ENTERPRISE" } })),
}));
vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: vi.fn(),
}));

const community: Community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "Patterns and practices.",
  status: "ACTIVE",
  memberCount: 120,
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

// Distinct, increasing joinedAt timestamps: unordered by construction, so a
// test can prove useFaces sorts newest-first rather than merely counting.
// Each member's name is its own letter (A, B, C...), so index order is
// directly readable off the rendered initials.
function membersFixture(count: number) {
  return Array.from({ length: count }, (_, index) => ({
    id: index,
    communityId: 1,
    userId: `user-${index}`,
    role: "MEMBER" as const,
    joinedAt: new Date(2026, 4, 1 + index).toISOString(),
    updatedAt: "2026-05-31T10:00:00Z",
  }));
}

function usersFixture(count: number): Record<string, { displayName: string }> {
  return Object.fromEntries(
    Array.from({ length: count }, (_, index) => [
      `user-${index}`,
      { displayName: String.fromCharCode(65 + index) },
    ]),
  );
}

function renderHero(memberCount: number) {
  vi.mocked(useManagementAccess).mockReturnValue({
    editCommunity: false,
    lookAndFeel: false,
    members: false,
    contributionPolicy: false,
    archive: false,
    managementPolicy: false,
    curation: false,
    health: false,
    invite: false,
    communication: false,
    credit: false,
    any: false,
    isLoading: false,
  });
  vi.mocked(useMyMembership).mockReturnValue({
    data: { role: "MEMBER" },
    isError: false,
  } as never);
  vi.mocked(useMembers).mockReturnValue({
    data: { content: membersFixture(memberCount) },
  } as never);
  vi.mocked(useUsers).mockReturnValue({
    data: usersFixture(memberCount),
  } as never);
  return render(
    <MemoryRouter>
      <IdentityHero community={community} slug="backend-guild" />
    </MemoryRouter>,
  );
}

describe("IdentityHero facepile", () => {
  it("shows at most five faces even when the community is larger", () => {
    // when - the community has more members than the facepile can show
    const { container } = renderHero(12);

    // then
    expect(container.querySelectorAll("[data-facepile-item]")).toHaveLength(5);
  });

  it("orders the facepile newest-first, not by join/creation order", () => {
    // when - membersFixture gives each member an increasing joinedAt, so the
    // highest index is the newest joiner and a naive "first page" read would
    // show the OLDEST five instead; a test only counting five faces could not
    // catch that regression.
    const { container } = renderHero(12);

    // then - newest five, newest first: indices 11..7 -> "L","K","J","I","H"
    const shown = [...container.querySelectorAll("[data-facepile-item]")].map(
      (item) => item.textContent,
    );
    expect(shown).toEqual(["L", "K", "J", "I", "H"]);
  });
});
