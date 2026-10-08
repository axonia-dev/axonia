import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAdminUsers } from "@/features/admin/api/use-admin-users";
import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { usePermissions } from "@/features/admin/api/use-permissions";
import type { AdminUser } from "@/features/admin/domain/admin-user";
import { UsersPage } from "@/features/admin/ui/UsersPage";
import { useUsers } from "@/features/identity/api/use-users";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/api/use-admin-users", () => ({
  useAdminUsers: vi.fn(),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

vi.mock("@/features/admin/api/use-held-permissions", () => ({
  useHeldPermissions: vi.fn(),
}));

vi.mock("@/features/identity/api/use-users");

vi.mock("@/features/admin/ui/GrantRoleControl", () => ({
  GrantRoleControl: () => <div>grant-role-control</div>,
}));

vi.mock("@/features/admin/api/use-revoke-role", () => ({
  useRevokeRole: () => ({ mutate: vi.fn(), isPending: false }),
}));

const mockUseAdminUsers = vi.mocked(useAdminUsers);
const mockUsePermissions = vi.mocked(usePermissions);
const mockUseHeldPermissions = vi.mocked(useHeldPermissions);
const mockUseUsers = vi.mocked(useUsers);

const roleless: AdminUser = {
  userId: "u1",
  displayName: "Alice",
  email: "alice@example.com",
  joinedAt: "2026-01-02T00:00:00Z",
  lastActiveAt: "2026-06-18T11:30:00Z",
  effectiveRoles: [],
  memberStatus: "PRESENT",
};

const withAdminRole: AdminUser = {
  ...roleless,
  effectiveRoles: [
    { role: "ADMIN", via: "DIRECT", bindingId: 1, roleId: 10, permissions: [] },
  ],
};

function serveUsers(users: AdminUser[]) {
  mockUseAdminUsers.mockReturnValue({
    data: {
      content: users,
      totalElements: users.length,
      totalPages: 1,
      number: 0,
      size: 20,
    },
    isPending: false,
  } as never);
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  // Empty set is safe here: roleless and withAdminRole both carry permissions: [], so
  // nothing in this file's fixtures is ever out of reach.
  mockUseHeldPermissions.mockReturnValue(new Set());
  mockUseUsers.mockReturnValue({ data: undefined } as never);
});

// A grant or revoke invalidates the users query, so the list refetches while the drawer is
// still open. The drawer has to read that fresh row, otherwise the mutation silently appears
// to do nothing and the new role has no Revoke button until the drawer is reopened.
describe("UsersPage detail drawer against refetched data", () => {
  it("shows a role granted while the drawer is open", async () => {
    // given - the drawer is open on a member holding no roles
    serveUsers([roleless]);
    const { rerender } = render(<UsersPage />);
    await user.click(screen.getByText("Alice"));
    expect(await screen.findByText("No roles assigned.")).toBeInTheDocument();

    // when - the grant lands and the invalidated users query serves the new row
    serveUsers([withAdminRole]);
    rerender(<UsersPage />);

    // then - the open drawer reflects it, Revoke included
    const drawer = screen.getByRole("dialog");
    expect(drawer).toHaveTextContent("ADMIN");
    expect(drawer).not.toHaveTextContent("No roles assigned.");
    expect(screen.getByRole("button", { name: /revoke/i })).toBeInTheDocument();
  });

  it("drops a role revoked while the drawer is open", async () => {
    // given - the drawer is open on a member holding a direct role
    serveUsers([withAdminRole]);
    const { rerender } = render(<UsersPage />);
    await user.click(screen.getByText("Alice"));
    expect(await screen.findByRole("dialog")).toHaveTextContent("ADMIN");

    // when - the revoke lands and the invalidated users query serves the new row
    serveUsers([roleless]);
    rerender(<UsersPage />);

    // then
    const drawer = screen.getByRole("dialog");
    expect(drawer).toHaveTextContent("No roles assigned.");
    expect(
      screen.queryByRole("button", { name: /revoke/i }),
    ).not.toBeInTheDocument();
  });

  it("keeps the drawer open across the refetch", async () => {
    // given
    serveUsers([roleless]);
    const { rerender } = render(<UsersPage />);
    await user.click(screen.getByText("Alice"));
    await screen.findByRole("dialog");

    // when
    serveUsers([withAdminRole]);
    rerender(<UsersPage />);

    // then - grant and revoke are in-place edits of a detail view, not dialog-closing forms
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
