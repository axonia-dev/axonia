import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useRevokeRole } from "@/features/admin/api/use-revoke-role";
import { UserDetailDrawer } from "@/features/admin/ui/UserDetailDrawer";
import { permissionState } from "@/test/permissions";

// Split out of UserDetailDrawer.test.tsx (which was pushing eslint's max-lines): the drawer
// header's own standing treatment, covering a deactivated member, an erased one, and one
// the directory holds no email for.
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

const aliceUser = {
  userId: "u1",
  displayName: "Alice",
  email: "alice@example.com",
  joinedAt: "2026-01-02T00:00:00.000Z",
  lastActiveAt: "2026-06-01T00:00:00.000Z",
  memberStatus: "PRESENT" as const,
  effectiveRoles: [
    {
      role: "ADMIN",
      via: "DIRECT",
      bindingId: 1,
      roleId: 10,
      permissions: [],
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  // Empty set is safe here: aliceUser's fixture carries permissions: [], so nothing in
  // this file is ever out of reach.
  mockUseHeldPermissions.mockReturnValue(new Set());
  mockUseRevokeRole.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("UserDetailDrawer member status", () => {
  it("renders no email paragraph for a member who has none", () => {
    // given - SCIM accepts a user with no userName, so email can be absent
    const { container } = render(
      <UserDetailDrawer
        user={{ ...aliceUser, email: null }}
        isOpen={true}
        onClose={vi.fn()}
      />,
    );

    // then - no empty paragraph holding the flex gap open under the title
    expect(
      container.querySelector("p.text-muted-foreground.text-sm"),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Alice")).toBeInTheDocument();
  });

  it("marks a deactivated member's header with (Deactivated)", () => {
    // given
    const deactivatedUser = {
      ...aliceUser,
      memberStatus: "DEACTIVATED" as const,
    };

    // when
    render(
      <UserDetailDrawer
        user={deactivatedUser}
        isOpen={true}
        onClose={vi.fn()}
      />,
    );

    // then
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("(Deactivated)")).toBeInTheDocument();
  });

  it("shows an erased member's header under the neutral label, muted and italic", () => {
    // given
    const erasedUser = {
      ...aliceUser,
      displayName: "Deleted member",
      memberStatus: "ERASED" as const,
    };

    // when
    render(
      <UserDetailDrawer user={erasedUser} isOpen={true} onClose={vi.fn()} />,
    );

    // then
    expect(screen.getByText("Deleted member")).toHaveClass(
      "text-muted-foreground",
      "italic",
    );
  });
});
