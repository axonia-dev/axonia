import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { ManagementAccess } from "@/features/community/api/use-management-access";
import type { Community } from "@/features/community/domain/community";
import { CommunityMenu } from "@/features/community/ui/CommunityMenu";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const { useCurrentOrganizationMock, usePermissionsMock, leaveCommunityMock } =
  vi.hoisted(() => ({
    useCurrentOrganizationMock: vi.fn(
      (): { data: { kind: string } | undefined } => ({ data: undefined }),
    ),
    usePermissionsMock: vi.fn((): { can: (permission: string) => boolean } => ({
      can: () => false,
    })),
    leaveCommunityMock: vi.fn(() => ({
      isReady: true,
      isHub: false,
      isPending: false,
      error: null,
      leave: vi.fn(),
    })),
  }));

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: useCurrentOrganizationMock,
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: usePermissionsMock,
}));

vi.mock("@/features/community/api/use-leave-community", () => ({
  useLeaveCommunity: leaveCommunityMock,
}));

vi.mock("@/features/community/api/use-communities", () => ({
  useArchiveCommunity: vi.fn(() => ({
    isPending: false,
    error: null,
    mutate: vi.fn(),
  })),
}));

vi.mock("@/auth/session", () => ({
  useAuth: () => ({ signinRedirect: vi.fn() }),
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

function fullAccess(overrides?: Partial<ManagementAccess>): ManagementAccess {
  return {
    editCommunity: true,
    lookAndFeel: true,
    members: true,
    contributionPolicy: true,
    archive: true,
    managementPolicy: true,
    curation: true,
    health: true,
    invite: true,
    communication: true,
    credit: true,
    any: true,
    isLoading: false,
    ...overrides,
  };
}

function renderMenu(options?: {
  community?: Partial<Community>;
  access?: Partial<ManagementAccess>;
}) {
  render(
    <MemoryRouter>
      <CommunityMenu
        community={{ ...community, ...options?.community }}
        slug="backend-guild"
        access={fullAccess(options?.access)}
      />
    </MemoryRouter>,
  );
}

async function openMenu() {
  await user.click(screen.getByRole("button", { name: /community actions/i }));
}

describe("CommunityMenu leave", () => {
  afterEach(() => {
    useCurrentOrganizationMock.mockReturnValue({ data: undefined });
    usePermissionsMock.mockReturnValue({ can: () => false });
    leaveCommunityMock.mockReturnValue({
      isReady: true,
      isHub: false,
      isPending: false,
      error: null,
      leave: vi.fn(),
    });
  });

  it("offers Leave community to a member with no delegated access: the menu is that one item", async () => {
    // when - every access flag closed, which is what a plain member has
    renderMenu({
      access: {
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
      },
    });
    await openMenu();

    // then
    expect(screen.getAllByRole("menuitem")).toHaveLength(1);
    expect(
      screen.getByRole("menuitem", { name: /leave community/i }),
    ).toBeInTheDocument();
  });

  it("leaves at once in an organization, with no confirmation", async () => {
    // given
    const leave = vi.fn();
    leaveCommunityMock.mockReturnValue({
      isReady: true,
      isHub: false,
      isPending: false,
      error: null,
      leave,
    });

    // when
    renderMenu();
    await openMenu();

    // then - the full-access menu still builds three groups with a rule
    // between each, proving the grouped collection renders correctly
    expect(screen.getAllByRole("separator")).toHaveLength(2);

    await user.click(
      screen.getByRole("menuitem", { name: /leave community/i }),
    );

    // then
    expect(leave).toHaveBeenCalledOnce();
    expect(screen.queryByRole("alertdialog")).toBeNull();
  });

  it("asks first in a hub, where leaving ends the workspace membership", async () => {
    // given
    const leave = vi.fn();
    leaveCommunityMock.mockReturnValue({
      isReady: true,
      isHub: true,
      isPending: false,
      error: null,
      leave,
    });

    // when
    renderMenu();
    await openMenu();
    await user.click(
      screen.getByRole("menuitem", { name: /leave community/i }),
    );

    // then - nothing has happened yet; the dialog now owns the decision
    expect(leave).not.toHaveBeenCalled();
    expect(screen.getByText("Leave this community?")).toBeInTheDocument();
  });

  it("withholds Leave until the organization kind is known", async () => {
    // given - until then we cannot tell whether leaving exits a tenant
    const leave = vi.fn();
    leaveCommunityMock.mockReturnValue({
      isReady: false,
      isHub: false,
      isPending: false,
      error: null,
      leave,
    });

    // when
    renderMenu();
    await openMenu();
    const leaveItem = screen.getByRole("menuitem", {
      name: /leave community/i,
    });

    // then - disabled in appearance
    expect(leaveItem).toHaveAttribute("data-disabled");

    // when - a click still lands on the disabled item
    await user.click(leaveItem);

    // then - the guard actually blocks the action, not just its look
    expect(leave).not.toHaveBeenCalled();
    expect(screen.queryByRole("alertdialog")).toBeNull();
  });

  it("keeps Leave community once the community is archived", async () => {
    // when
    renderMenu({ community: { status: "ARCHIVED" } });
    await openMenu();

    // then
    expect(
      screen.getByRole("menuitem", { name: /leave community/i }),
    ).toBeInTheDocument();
  });
});
