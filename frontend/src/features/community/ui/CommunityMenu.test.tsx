import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { ManagementAccess } from "@/features/community/api/use-management-access";
import type { Community } from "@/features/community/domain/community";
import { CommunityMenu } from "@/features/community/ui/CommunityMenu";
import { communityFixture } from "@/test/community-fixture";

let user: ReturnType<typeof userEvent.setup>;
beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const { useCurrentOrganizationMock, usePermissionsMock } = vi.hoisted(() => ({
  useCurrentOrganizationMock: vi.fn(
    (): { data: { kind: string } | undefined } => ({ data: undefined }),
  ),
  usePermissionsMock: vi.fn((): { can: (permission: string) => boolean } => ({
    can: () => false,
  })),
}));

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: useCurrentOrganizationMock,
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: usePermissionsMock,
}));

// This file exercises the management items only; Leave community's own
// behaviour (instant leave, hub confirmation, disabled until ready) lives in
// CommunityMenu.leave.test.tsx. Leave is always ready and non-hub here so it
// never interferes with these assertions.
vi.mock("@/features/community/api/use-leave-community", () => ({
  useLeaveCommunity: () => ({
    isReady: true,
    isHub: false,
    isPending: false,
    error: null,
    leave: vi.fn(),
  }),
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

const community = communityFixture({
  description: "Patterns and practices.",
  memberCount: 120,
});

function LocationDisplay() {
  return <div>at {useLocation().pathname}</div>;
}

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
    member: true,
    isLoading: false,
    ...overrides,
  };
}

function renderMenu(options?: {
  community?: Partial<Community>;
  access?: Partial<ManagementAccess>;
}) {
  render(
    <MemoryRouter initialEntries={["/start"]}>
      <Routes>
        <Route
          path="/start"
          element={
            <CommunityMenu
              community={{ ...community, ...options?.community }}
              slug="backend-guild"
              access={fullAccess(options?.access)}
            />
          }
        />
        <Route path="*" element={<LocationDisplay />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function openMenu() {
  await user.click(screen.getByRole("button", { name: /community actions/i }));
}

describe("CommunityMenu", () => {
  afterEach(() => {
    useCurrentOrganizationMock.mockReturnValue({ data: undefined });
    usePermissionsMock.mockReturnValue({ can: () => false });
  });

  it("lists Mentoring settings for an organizer only", async () => {
    // when
    renderMenu({ access: { managementPolicy: true } });
    await openMenu();

    // then
    expect(
      screen.getByRole("menuitem", { name: "Mentoring" }),
    ).toBeInTheDocument();
  });

  it("leaves Mentoring settings out for anyone who is not an organizer", async () => {
    // when
    renderMenu({ access: { managementPolicy: false } });
    await openMenu();

    // then
    expect(
      screen.queryByRole("menuitem", { name: "Mentoring" }),
    ).not.toBeInTheDocument();
  });

  it("opens the mentoring settings", async () => {
    // given
    renderMenu();
    await openMenu();

    // when
    await user.click(screen.getByRole("menuitem", { name: "Mentoring" }));

    // then
    expect(
      screen.getByText("at /communities/backend-guild/settings/mentoring"),
    ).toBeInTheDocument();
  });

  it("offers Edit, Community policy and Archive for an active community", async () => {
    // when
    renderMenu();
    await openMenu();

    // then
    expect(screen.getByRole("menuitem", { name: "Edit" })).toBeInTheDocument();
    expect(
      screen.getByRole("menuitem", { name: /community policy/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("menuitem", { name: "Archive" }),
    ).toBeInTheDocument();
  });

  it("navigates to the edit page from the Edit item", async () => {
    // when
    renderMenu();
    await openMenu();
    await user.click(screen.getByRole("menuitem", { name: "Edit" }));

    // then
    expect(
      screen.getByText("at /communities/backend-guild/edit"),
    ).toBeInTheDocument();
  });

  it("navigates to the members page from the Members & roles item", async () => {
    // when
    renderMenu();
    await openMenu();
    await user.click(
      screen.getByRole("menuitem", { name: /members & roles/i }),
    );

    // then
    expect(
      screen.getByText("at /communities/backend-guild/settings/members"),
    ).toBeInTheDocument();
  });

  it("navigates to the community policy page from its item", async () => {
    // when
    renderMenu();
    await openMenu();
    await user.click(
      screen.getByRole("menuitem", { name: /community policy/i }),
    );

    // then
    expect(
      screen.getByText("at /communities/backend-guild/settings/policy"),
    ).toBeInTheDocument();
  });

  it("navigates to the look & feel editor from its item", async () => {
    // when
    renderMenu();
    await openMenu();
    await user.click(screen.getByRole("menuitem", { name: /look & feel/i }));

    // then
    expect(
      screen.getByText("at /communities/backend-guild/look-and-feel"),
    ).toBeInTheDocument();
  });

  it("navigates to the communication page from its item", async () => {
    // when
    renderMenu();
    await openMenu();
    await user.click(screen.getByRole("menuitem", { name: "Communication" }));

    // then
    expect(
      screen.getByText("at /communities/backend-guild/settings/communication"),
    ).toBeInTheDocument();
  });

  it("hides Communication for a member the axis does not open", async () => {
    // when
    renderMenu({ access: { communication: false } });
    await openMenu();

    // then
    expect(
      screen.queryByRole("menuitem", { name: "Communication" }),
    ).toBeNull();
  });

  it("withholds Edit and Archive once the community is archived", async () => {
    // when
    renderMenu({ community: { status: "ARCHIVED" } });
    await openMenu();

    // then - only the still-meaningful actions remain
    expect(screen.queryByRole("menuitem", { name: "Edit" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "Archive" })).toBeNull();
    expect(
      screen.getByRole("menuitem", { name: /community policy/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("menuitem", { name: /members & roles/i }),
    ).toBeInTheDocument();
  });

  it("keeps the Community policy item while a member may touch either policy", async () => {
    // when - a member who can change the contribution policy but not manage
    renderMenu({ access: { managementPolicy: false } });
    await openMenu();

    // then
    expect(
      screen.getByRole("menuitem", { name: /community policy/i }),
    ).toBeInTheDocument();
  });

  it("hides an item whose access flag is closed", async () => {
    // when - a member who may touch neither policy nor the member roster
    renderMenu({
      access: {
        members: false,
        contributionPolicy: false,
        managementPolicy: false,
      },
    });
    await openMenu();

    // then
    expect(
      screen.queryByRole("menuitem", { name: /members & roles/i }),
    ).toBeNull();
    expect(
      screen.queryByRole("menuitem", { name: /community policy/i }),
    ).toBeNull();
  });
});
