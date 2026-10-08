import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useRevokeRole } from "@/features/admin/api/use-revoke-role";
import { UserDetailDrawer } from "@/features/admin/ui/UserDetailDrawer";
import { permissionState } from "@/test/permissions";

// Split out of UserDetailDrawer.test.tsx (which was pushing eslint's max-lines): the
// privilege ceiling is its own contract - a role granting more than the caller
// holds offers Revoke present but refused, never removed.
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

vi.mock("@/features/admin/api/use-held-permissions", () => ({
  useHeldPermissions: vi.fn(),
}));

vi.mock("@/features/admin/api/use-revoke-role", () => ({
  useRevokeRole: vi.fn(),
}));

vi.mock("@/features/admin/ui/GrantRoleControl", () => ({
  GrantRoleControl: () => <div>grant-role-control</div>,
}));

const mockUsePermissions = vi.mocked(usePermissions);
const mockUseHeldPermissions = vi.mocked(useHeldPermissions);
const mockUseRevokeRole = vi.mocked(useRevokeRole);

const ownerUser = {
  userId: "u1",
  displayName: "Alice",
  email: "alice@example.com",
  joinedAt: "2026-01-02T00:00:00.000Z",
  lastActiveAt: "2026-06-01T00:00:00.000Z",
  memberStatus: "PRESENT" as const,
  effectiveRoles: [
    {
      role: "Owner",
      via: "USER",
      bindingId: 7,
      roleId: 2,
      permissions: ["organization:export"],
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  mockUseRevokeRole.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("UserDetailDrawer ceiling", () => {
  it("does not offer to revoke a role that reaches past the caller", () => {
    // given - the caller holds role:write but not organization:export, which Owner grants
    mockUseHeldPermissions.mockReturnValue(new Set(["role:write"]));

    // when
    render(<UserDetailDrawer user={ownerUser} isOpen onClose={vi.fn()} />);

    // then - present, but refused
    expect(screen.getByRole("button", { name: "Revoke" })).toBeDisabled();
    expect(
      screen.getByText("Reaches beyond what this account holds."),
    ).toBeInTheDocument();
  });

  it("offers a plain, enabled revoke when the role is within the caller's reach", () => {
    // given - the caller holds every permission Owner grants
    mockUseHeldPermissions.mockReturnValue(
      new Set(["role:write", "organization:export"]),
    );

    // when
    render(<UserDetailDrawer user={ownerUser} isOpen onClose={vi.fn()} />);

    // then - anchor: the button is there and not blocked by the ceiling
    expect(screen.getByRole("button", { name: "Revoke" })).toBeEnabled();
    expect(
      screen.queryByText("Reaches beyond what this account holds."),
    ).not.toBeInTheDocument();
  });

  it("offers revoke on a role whose only unheld code is one this edition does not know", () => {
    // given - sso:read is a Cloud code the open edition grants nothing by, so the server leaves it out
    mockUseHeldPermissions.mockReturnValue(
      new Set(["role:write", "organization:export"]),
    );
    const restored = {
      ...ownerUser,
      effectiveRoles: ownerUser.effectiveRoles.map((role) => ({
        ...role,
        permissions: ["organization:export", "sso:read"],
      })),
    };

    // when
    render(<UserDetailDrawer user={restored} isOpen onClose={vi.fn()} />);

    // then
    expect(screen.getByRole("button", { name: "Revoke" })).toBeEnabled();
  });
});
