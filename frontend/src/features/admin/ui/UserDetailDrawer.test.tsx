import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useRevokeRole } from "@/features/admin/api/use-revoke-role";
import { UserDetailDrawer } from "@/features/admin/ui/UserDetailDrawer";
import { ApiError } from "@/lib/api-client";
import { inEdition, WITH_AUDIT } from "@/test/edition-fixtures";
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

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  // Covers every permission code the roles fixtures below carry, so the ceiling
  // never blocks a revoke this file's other tests already assert is offered.
  mockUseHeldPermissions.mockReturnValue(
    new Set(["member:read", "member:write", "scim:write", "future:capability"]),
  );
  mockUseRevokeRole.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
});

describe("UserDetailDrawer", () => {
  it("renders the user's role names", () => {
    // given / when
    render(<UserDetailDrawer user={aliceUser} isOpen onClose={vi.fn()} />);

    // then
    expect(screen.getByText("ADMIN")).toBeInTheDocument();
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
  });

  it("shows the Revoke button for a DIRECT role when the user has role:write", () => {
    // given / when
    render(<UserDetailDrawer user={aliceUser} isOpen onClose={vi.fn()} />);

    // then
    expect(screen.getByRole("button", { name: /revoke/i })).toBeInTheDocument();
  });

  it("hides the Revoke button when the user lacks role:write", () => {
    // given
    mockUsePermissions.mockReturnValue(permissionState(() => false));

    // when
    render(<UserDetailDrawer user={aliceUser} isOpen onClose={vi.fn()} />);

    // then
    expect(
      screen.queryByRole("button", { name: /revoke/i }),
    ).not.toBeInTheDocument();
  });

  it("calls useRevokeRole mutate with the correct bindingId", async () => {
    // given
    const mutate = vi.fn();
    mockUseRevokeRole.mockReturnValue({ mutate, isPending: false } as never);

    // when
    render(<UserDetailDrawer user={aliceUser} isOpen onClose={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: /revoke/i }));

    // then
    expect(mutate.mock.calls[0]?.[0]).toBe(1);
  });

  it("surfaces the LAST_ADMIN friendly message on a 409 self-lockout", async () => {
    // given
    const mutate = vi.fn(
      (_id: number, opts?: { onError?: (e: unknown) => void }) => {
        opts?.onError?.(new ApiError(409, "Self-lockout", "LAST_ADMIN"));
      },
    );
    mockUseRevokeRole.mockReturnValue({ mutate, isPending: false } as never);

    // when
    render(<UserDetailDrawer user={aliceUser} isOpen onClose={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: /revoke/i }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "This is the signed-in account's last admin access, and cannot be revoked here.",
    );
  });

  it("hides GrantRoleControl when the user lacks role:write", () => {
    // given
    mockUsePermissions.mockReturnValue(permissionState(() => false));

    // when
    render(<UserDetailDrawer user={aliceUser} isOpen onClose={vi.fn()} />);

    // then
    expect(screen.queryByText("grant-role-control")).not.toBeInTheDocument();
  });

  it("shows GrantRoleControl when the user has role:write", () => {
    // given / when
    render(<UserDetailDrawer user={aliceUser} isOpen onClose={vi.fn()} />);

    // then
    expect(screen.getByText("grant-role-control")).toBeInTheDocument();
  });

  it("shows member-since and last-active in the drawer", () => {
    // given / when
    render(<UserDetailDrawer user={aliceUser} isOpen onClose={vi.fn()} />);

    // then
    expect(screen.getByText(/member since/i)).toBeInTheDocument();
    expect(
      screen.getByText(/active|last seen|never signed in/i),
    ).toBeInTheDocument();
  });

  it("shows '· via group' chip label and no Revoke button for GROUP roles", () => {
    // given
    const groupUser = {
      ...aliceUser,
      effectiveRoles: [
        {
          role: "VIEWER",
          via: "GROUP",
          bindingId: null,
          roleId: 20,
          permissions: [],
        },
      ],
    };

    // when
    render(<UserDetailDrawer user={groupUser} isOpen onClose={vi.fn()} />);

    // then
    expect(screen.getByText(/via group/i)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /revoke/i }),
    ).not.toBeInTheDocument();
  });

  it("renders '-' for Member since when joinedAt is null", () => {
    // given
    const userWithNullJoinedAt = { ...aliceUser, joinedAt: null };

    // when
    render(
      <UserDetailDrawer user={userWithNullJoinedAt} isOpen onClose={vi.fn()} />,
    );

    // then
    expect(screen.getByText("-")).toBeInTheDocument();
  });

  it("shows each role with its provenance and permissions", () => {
    // given
    const userWithPerms = {
      userId: "u1",
      displayName: "Alice",
      email: "a@x.io",
      joinedAt: null,
      lastActiveAt: null,
      memberStatus: "PRESENT" as const,
      effectiveRoles: [
        {
          role: "Member Manager",
          via: "DIRECT",
          bindingId: 1,
          roleId: 10,
          permissions: ["member:read", "member:write"],
        },
      ],
    };

    // when
    render(<UserDetailDrawer user={userWithPerms} isOpen onClose={vi.fn()} />);

    // then
    expect(screen.getByText("Member Manager")).toBeInTheDocument();
    expect(screen.getByText(/direct/i)).toBeInTheDocument();
    expect(screen.getByText("View members")).toBeInTheDocument();
    expect(screen.getByText("Manage members")).toBeInTheDocument();
  });

  it("lists permissions by human label, grouped, not as codes", () => {
    // given
    const userWithScimWrite = {
      ...aliceUser,
      effectiveRoles: [
        {
          role: "Directory Admin",
          via: "DIRECT",
          bindingId: 2,
          roleId: 30,
          permissions: ["scim:write"],
        },
      ],
    };

    // when
    render(
      <UserDetailDrawer user={userWithScimWrite} isOpen onClose={vi.fn()} />,
    );

    // then
    expect(screen.getByText("Manage directory sync")).toBeInTheDocument();
    expect(
      screen.getByText("Issue and revoke SCIM provisioning tokens."),
    ).toBeInTheDocument();
    expect(screen.queryByText("scim:write")).not.toBeInTheDocument();
  });

  it("falls back to the raw code for an unknown permission", () => {
    // given
    const mystery = {
      ...aliceUser,
      effectiveRoles: [
        {
          role: "Mystery",
          via: "DIRECT",
          bindingId: 3,
          roleId: 40,
          permissions: ["future:capability"],
        },
      ],
    };

    // when
    render(<UserDetailDrawer user={mystery} isOpen onClose={vi.fn()} />);

    // then
    expect(screen.getByText("future:capability")).toBeInTheDocument();
  });

  it("labels a permission the edition contributes rather than showing its code", () => {
    // given
    const auditor = {
      ...aliceUser,
      effectiveRoles: aliceUser.effectiveRoles.map((role) => ({
        ...role,
        permissions: ["audit:read"],
      })),
    };

    // when
    render(<UserDetailDrawer user={auditor} isOpen onClose={vi.fn()} />, {
      wrapper: inEdition(WITH_AUDIT),
    });

    // then
    expect(screen.getByText("View the audit trail")).toBeInTheDocument();
    expect(screen.queryByText("audit:read")).not.toBeInTheDocument();
  });

  // Deactivated/erased header treatment is covered in UserDetailDrawer.memberStatus.test.tsx.
});
