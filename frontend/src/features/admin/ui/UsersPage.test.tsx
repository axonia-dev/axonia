import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAdminUsers } from "@/features/admin/api/use-admin-users";
import { usePermissions } from "@/features/admin/api/use-permissions";
import type { AdminUser } from "@/features/admin/domain/admin-user";
import { UsersPage } from "@/features/admin/ui/UsersPage";
import { useUsers } from "@/features/identity/api/use-users";
import { kindHue } from "@/lib/kind-hue";
import { userHue } from "@/lib/user-hue";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/api/use-admin-users", () => ({
  useAdminUsers: vi.fn(),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

vi.mock("@/features/identity/api/use-users");

vi.mock("@/features/admin/ui/UserDetailDrawer", () => ({
  UserDetailDrawer: () => <div>user-detail-drawer</div>,
}));

const mockUseAdminUsers = vi.mocked(useAdminUsers);
const mockUsePermissions = vi.mocked(usePermissions);
const mockUseUsers = vi.mocked(useUsers);

// Typed explicitly (not inferred): the mocked hook's return value is cast `as never` below,
// which would otherwise let a fixture missing `memberStatus` through undetected by `tsc -b`.
const aliceUser: AdminUser = {
  userId: "u1",
  displayName: "Alice",
  email: "alice@example.com",
  joinedAt: "2026-01-02T00:00:00Z",
  lastActiveAt: "2026-06-18T11:30:00Z",
  effectiveRoles: [
    { role: "ADMIN", via: "DIRECT", bindingId: 1, roleId: 10, permissions: [] },
  ],
  memberStatus: "PRESENT",
};

/** One page of the roster, holding everyone. The tally and the pager, where the page is a
 *  slice of a larger roster, live in `UsersPage.paging.test.tsx`. */
const listing = (users: AdminUser[]) => ({
  content: users,
  totalElements: users.length,
  totalPages: 1,
  number: 0,
  size: 20,
});

const pendingPat: AdminUser = {
  userId: null,
  displayName: "Pending Pat",
  email: "pat@example.com",
  joinedAt: null,
  lastActiveAt: null,
  effectiveRoles: [],
  memberStatus: "PRESENT",
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  mockUseAdminUsers.mockReturnValue({
    data: listing([aliceUser]),
    isPending: false,
  } as never);
  mockUseUsers.mockReturnValue({ data: undefined } as never);
});

describe("UsersPage", () => {
  it("lists users with display name and role chips, not email", () => {
    render(<UsersPage />);

    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("ADMIN")).toBeInTheDocument();
    // email lives in the detail drawer now, not the row
    expect(screen.queryByText("alice@example.com")).not.toBeInTheDocument();
  });

  it("shows a person count and an activity line", () => {
    render(<UsersPage />);

    expect(screen.getByText(/1 (person|people)/i)).toBeInTheDocument();
    expect(
      screen.getByText(/active|last seen|never signed in/i),
    ).toBeInTheDocument();
  });

  it("shows a '· via group' marker for GROUP roles", () => {
    // given
    mockUseAdminUsers.mockReturnValue({
      data: listing([
        {
          ...aliceUser,
          effectiveRoles: [
            {
              role: "ADMIN",
              via: "GROUP",
              bindingId: null,
              roleId: 10,
              permissions: [],
            },
          ],
        },
      ]),
      isPending: false,
    } as never);

    // when
    render(<UsersPage />);

    // then
    expect(screen.getByText(/via group/i)).toBeInTheDocument();
  });

  it("shows the empty state when no users match the search", () => {
    // given
    mockUseAdminUsers.mockReturnValue({
      data: listing([]),
      isPending: false,
    } as never);

    // when
    render(<UsersPage />);

    // then
    expect(screen.getByText("No users found")).toBeInTheDocument();
  });

  it("shows skeletons when the query is pending", () => {
    // given
    mockUseAdminUsers.mockReturnValue({
      data: undefined,
      isPending: true,
    } as never);

    // when
    const { container } = render(<UsersPage />);

    // then
    // Skeleton renders motion-safe:animate-pulse; use substring match
    expect(
      container.querySelectorAll("[class*='animate-pulse']").length,
    ).toBeGreaterThan(0);
  });

  it("blocks access when the user lacks member:read", () => {
    // given
    mockUsePermissions.mockReturnValue(permissionState(() => false));

    // when
    render(<UsersPage />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent("No access to this.");
    expect(screen.queryByText("Alice")).not.toBeInTheDocument();
  });

  it("passes the search query to useAdminUsers as the user types", async () => {
    // given
    render(<UsersPage />);

    // when
    await user.type(screen.getByRole("searchbox"), "alice");

    // then
    expect(mockUseAdminUsers).toHaveBeenCalledWith(
      expect.objectContaining({ q: "alice" }),
    );
  });

  it("draws a person in the person palette and the round person shape", () => {
    render(<UsersPage />);

    const monogram = screen.getByText("A");
    expect(monogram.style.getPropertyValue("--k")).toBe(userHue("u1"));
    expect(monogram.style.getPropertyValue("--k")).not.toBe(kindHue("u1"));
    expect(monogram).toHaveClass("rounded-full");
  });

  it("shows profile pictures when identity resolves them", () => {
    mockUseUsers.mockReturnValue({
      data: {
        u1: {
          id: "u1",
          displayName: "Alice",
          avatarUrl: "https://cdn.example.com/alice.png",
        },
      },
    } as never);

    render(<UsersPage />);

    expect(screen.getByRole("img", { name: "Alice" })).toHaveAttribute(
      "src",
      "https://cdn.example.com/alice.png",
    );
  });

  it("lists a SCIM-provisioned member (null userId) as Pending without breaking the list", () => {
    // given - a directory member synced by SCIM who has never signed in has no userId
    mockUseAdminUsers.mockReturnValue({
      data: listing([aliceUser, pendingPat]),
      isPending: false,
    } as never);

    // when
    render(<UsersPage />);

    // then - both the signed-in member and the pending member render; the pending one is badged
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Pending Pat")).toBeInTheDocument();
    expect(screen.getByText("Pending")).toBeInTheDocument();
  });

  it("marks a deactivated member's row with (Deactivated)", () => {
    // given
    mockUseAdminUsers.mockReturnValue({
      data: listing([{ ...aliceUser, memberStatus: "DEACTIVATED" }]),
      isPending: false,
    } as never);

    // when
    render(<UsersPage />);

    // then
    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("(Deactivated)")).toBeInTheDocument();
  });

  it("renders an erased member's row under the neutral label, muted and italic", () => {
    // given
    mockUseAdminUsers.mockReturnValue({
      data: listing([
        {
          ...aliceUser,
          userId: "u-erased",
          displayName: "Deleted member",
          email: null,
          memberStatus: "ERASED",
        },
      ]),
      isPending: false,
    } as never);

    // when
    render(<UsersPage />);

    // then
    expect(screen.getByText("Deleted member")).toHaveClass(
      "text-muted-foreground",
      "italic",
    );
  });
});
