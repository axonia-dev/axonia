import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import type { ManagementAccess } from "@/features/community/api/use-management-access";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import {
  useMembers,
  useMyMembership,
} from "@/features/community/api/use-membership";
import type { Community } from "@/features/community/domain/community";
import type { CommunityRole } from "@/features/community/domain/membership";
import { IdentityHero } from "@/features/community/ui/IdentityHero";
import { useUsers } from "@/features/identity/api/use-users";

const { useCurrentOrganizationMock } = vi.hoisted(() => ({
  useCurrentOrganizationMock: vi.fn(
    (): { data: { kind: string } | undefined } => ({
      data: { kind: "ENTERPRISE" },
    }),
  ),
}));

vi.mock("@/features/community/ui/JoinAction", () => ({
  JoinAction: () => <div>join-action</div>,
}));
vi.mock("@/features/community/ui/MembershipChip", () => ({
  MembershipChip: ({ role }: { role: string }) => <div>chip-{role}</div>,
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
  useCurrentOrganization: useCurrentOrganizationMock,
}));
vi.mock("@/features/identity/api/use-users", () => ({
  useUsers: vi.fn(),
}));

function access(any: boolean): ManagementAccess {
  return {
    editCommunity: any,
    lookAndFeel: any,
    members: any,
    contributionPolicy: any,
    archive: any,
    managementPolicy: any,
    curation: any,
    health: any,
    invite: any,
    communication: any,
    credit: any,
    any,
    isLoading: false,
  };
}

function accessWithInvite(invite: boolean): ManagementAccess {
  return { ...access(false), invite, any: invite };
}

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

interface RenderHeroOverrides {
  community?: Community;
  canManage?: boolean;
  access?: ManagementAccess;
  organizationKind?: string;
  membership?: { role: CommunityRole } | null | undefined;
  isError?: boolean;
}

// A missing `membership` override reads as `undefined` through optional
// chaining, same as an explicit `membership: undefined` - so only an `in`
// check tells "not supplied, default to a visitor" apart from "supplied as
// undefined, the query is still in flight". Kept outside renderHero so this
// branch doesn't add to its own eslint `complexity` count.
function membershipOverride(
  overrides?: RenderHeroOverrides,
): { role: CommunityRole } | null | undefined {
  if (!overrides || !("membership" in overrides)) {
    return null;
  }
  return overrides.membership;
}

// Kept outside renderHero for the same reason as membershipOverride: it holds
// its own default so renderHero doesn't grow another `??` toward its
// complexity budget.
function isErrorOverride(overrides?: RenderHeroOverrides): boolean {
  return overrides?.isError ?? false;
}

function renderHero(overrides?: RenderHeroOverrides) {
  vi.mocked(useManagementAccess).mockReturnValue(
    overrides?.access ?? access(overrides?.canManage ?? false),
  );
  useCurrentOrganizationMock.mockReturnValue({
    data: { kind: overrides?.organizationKind ?? "ENTERPRISE" },
  });
  vi.mocked(useMyMembership).mockReturnValue({
    data: membershipOverride(overrides),
    isError: isErrorOverride(overrides),
  } as never);
  vi.mocked(useMembers).mockReturnValue({ data: { content: [] } } as never);
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
  return render(
    <MemoryRouter>
      <IdentityHero
        community={overrides?.community ?? community}
        slug="backend-guild"
      />
    </MemoryRouter>,
  );
}

describe("IdentityHero", () => {
  it("presents the guild identity", () => {
    // when
    renderHero();

    // then
    expect(
      screen.getByRole("heading", { name: "Backend Guild" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Patterns and practices.")).toBeInTheDocument();
  });

  it("offers a visitor the join call-to-action and no menu at all", () => {
    // when - a non-member: management access is derived from membership, so a
    // visitor can never have any
    renderHero();

    // then
    expect(screen.getByText("join-action")).toBeInTheDocument();
    expect(screen.queryByText("community-menu")).toBeNull();
  });

  it("states membership rather than inviting the member to leave", () => {
    // when
    renderHero({ membership: { role: "MEMBER" }, canManage: false });

    // then - no Leave button anywhere near the call-to-action slot
    expect(screen.getByText("chip-MEMBER")).toBeInTheDocument();
    expect(screen.queryByText("join-action")).toBeNull();
  });

  it("gives every member the menu, even one with no delegated access", () => {
    // when - the menu is where Leave lives, so a plain member must still get it
    renderHero({ membership: { role: "MEMBER" }, canManage: false });

    // then
    expect(screen.getByText("community-menu")).toBeInTheDocument();
  });

  it("names the organizer's role", () => {
    // when
    renderHero({ membership: { role: "ORGANIZER" }, canManage: true });

    // then
    expect(screen.getByText("chip-ORGANIZER")).toBeInTheDocument();
  });

  it("commits to neither Join nor the chip while membership is still unknown", () => {
    // when - the query has not resolved: showing Join to a member would be a lie
    renderHero({ membership: undefined });

    // then
    expect(screen.queryByText("join-action")).toBeNull();
    expect(screen.queryByText("community-menu")).toBeNull();
  });

  it("falls back to Join when the membership query has failed, instead of a blank strip", () => {
    // when - data is undefined on both a loading AND a failed query; isError is
    // what tells the two apart, and a failed lookup should not lock a visitor
    // out of joining
    renderHero({ membership: undefined, isError: true });

    // then
    expect(screen.getByText("join-action")).toBeInTheDocument();
    expect(screen.queryByText("community-menu")).toBeNull();
  });

  it("marks an archived community", () => {
    // when
    renderHero({ community: { ...community, status: "ARCHIVED" } });

    // then
    expect(screen.getByText("Archived")).toBeInTheDocument();
  });

  it("offers no Invite people where the edition supplies no invite, even in a hub org with the axis open", () => {
    // when
    renderHero({
      organizationKind: "HUB",
      access: accessWithInvite(true),
    });

    // then
    expect(screen.queryByRole("button", { name: /invite people/i })).toBeNull();
  });
});

// Facepile cap and ordering behaviour lives in IdentityHero.facepile.test.tsx,
// alongside the member/user fixtures those tests need.
