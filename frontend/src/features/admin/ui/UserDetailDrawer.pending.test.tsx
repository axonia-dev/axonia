import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useRevokeRole } from "@/features/admin/api/use-revoke-role";
import type { AdminUser } from "@/features/admin/domain/admin-user";
import { UserDetailDrawer } from "@/features/admin/ui/UserDetailDrawer";
import { permissionState } from "@/test/permissions";

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

const aliceUser: AdminUser = {
  userId: "u1",
  displayName: "Alice",
  email: "alice@example.com",
  joinedAt: "2026-01-02T00:00:00.000Z",
  lastActiveAt: null,
  memberStatus: "PRESENT",
  signedIn: false,
  effectiveRoles: [],
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(usePermissions).mockReturnValue(permissionState(() => true));
  vi.mocked(useHeldPermissions).mockReturnValue(
    new Set(["member:read", "member:write"]),
  );
  vi.mocked(useRevokeRole).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("UserDetailDrawer for somebody who has not signed in yet", () => {
  it("says somebody provisioned with their subject has not signed in, and lets roles be granted", () => {
    // when
    render(<UserDetailDrawer user={aliceUser} isOpen onClose={vi.fn()} />);

    // then
    expect(
      screen.getByText(
        "Provisioned from the directory, but has not signed in yet.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("grant-role-control")).toBeInTheDocument();
  });

  it("says roles wait for the first sign-in of somebody provisioned without a subject", () => {
    // when
    render(
      <UserDetailDrawer
        user={{ ...aliceUser, userId: null }}
        isOpen
        onClose={vi.fn()}
      />,
    );

    // then
    expect(
      screen.getByText(
        "Provisioned from the directory, but has not signed in yet. Roles can be granted once they complete their first sign-in.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("grant-role-control")).not.toBeInTheDocument();
  });
});
