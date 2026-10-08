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

// Leave community's own behaviour lives in CommunityMenu.leave.test.tsx; here
// it stays ready and non-hub so it never interferes with these assertions.
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

describe("CommunityMenu archive and delete", () => {
  afterEach(() => {
    useCurrentOrganizationMock.mockReturnValue({ data: undefined });
    usePermissionsMock.mockReturnValue({ can: () => false });
  });

  it("opens the archive confirmation dialog from the Archive item", async () => {
    // when
    renderMenu();
    await openMenu();
    await user.click(screen.getByRole("menuitem", { name: "Archive" }));

    // then - the slug-typed confirmation appears
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(screen.getByText("Archive this community?")).toBeInTheDocument();
  });

  it("offers Archive for an enterprise organization", async () => {
    // given
    useCurrentOrganizationMock.mockReturnValue({
      data: { kind: "ENTERPRISE" },
    });

    // when
    renderMenu();
    await openMenu();

    // then
    expect(
      screen.getByRole("menuitem", { name: "Archive" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("menuitem", { name: "Delete" })).toBeNull();
  });

  it("offers a hub org owner neither Archive nor Delete where the edition supplies no deletion", async () => {
    // given
    useCurrentOrganizationMock.mockReturnValue({ data: { kind: "HUB" } });
    usePermissionsMock.mockReturnValue({ can: () => true });

    // when
    renderMenu();
    await openMenu();

    // then
    expect(screen.queryByRole("menuitem", { name: "Archive" })).toBeNull();
    expect(screen.queryByRole("menuitem", { name: "Delete" })).toBeNull();
  });
});
