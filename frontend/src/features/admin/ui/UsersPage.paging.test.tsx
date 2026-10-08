import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useAdminUsers } from "@/features/admin/api/use-admin-users";
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

vi.mock("@/features/identity/api/use-users");

vi.mock("@/features/admin/ui/UserDetailDrawer", () => ({
  UserDetailDrawer: () => <div>user-detail-drawer</div>,
}));

const mockUseAdminUsers = vi.mocked(useAdminUsers);
const mockUsePermissions = vi.mocked(usePermissions);
const mockUseUsers = vi.mocked(useUsers);

const aliceUser: AdminUser = {
  userId: "u1",
  displayName: "Alice",
  email: "alice@example.com",
  joinedAt: "2026-01-02T00:00:00Z",
  lastActiveAt: "2026-06-18T11:30:00Z",
  effectiveRoles: [],
  memberStatus: "PRESENT",
};

/** One page of the roster. `totalElements` is stated separately from the rows given, because
 *  the whole point of these tests is that the two are not the same number. */
const listing = (users: AdminUser[], totalElements = users.length) => ({
  content: users,
  totalElements,
  totalPages: Math.max(1, Math.ceil(totalElements / 20)),
  number: 0,
  size: 20,
});

function serve(users: AdminUser[], totalElements?: number) {
  mockUseAdminUsers.mockReturnValue({
    data: listing(users, totalElements),
    isPending: false,
  } as never);
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  mockUseUsers.mockReturnValue({ data: undefined } as never);
  serve([aliceUser]);
});

// The roster is paged server-side, so the rows on screen are a slice of it. Every assertion
// here is about the difference between that slice and the organization's actual headcount -
// the console used to report the slice, which read as "0 of your people" once a tenant grew.
describe("UsersPage paging", () => {
  it("counts the whole organization, not the rows on this page", () => {
    // given - 42 people in the directory, 20 of them on this page
    serve(
      Array.from({ length: 20 }, () => aliceUser),
      42,
    );

    // when
    render(<UsersPage />);

    // then
    expect(screen.getByText("42 people")).toBeInTheDocument();
  });

  it("offers a pager when the roster outgrows one page", async () => {
    // given
    serve([aliceUser], 42);
    render(<UsersPage />);

    // when
    await user.click(screen.getByRole("button", { name: "Next" }));

    // then - the next page is fetched rather than the remaining 41 staying unreachable
    expect(mockUseAdminUsers).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1 }),
    );
  });

  it("does not offer a pager when everyone fits on one page", () => {
    render(<UsersPage />);

    expect(
      screen.queryByRole("navigation", { name: "Pagination" }),
    ).not.toBeInTheDocument();
  });

  it("returns to the first page when the search narrows", async () => {
    // given - the reader has paged forward
    serve([aliceUser], 42);
    render(<UsersPage />);
    await user.click(screen.getByRole("button", { name: "Next" }));

    // when
    await user.type(screen.getByRole("searchbox"), "a");

    // then
    expect(mockUseAdminUsers).toHaveBeenLastCalledWith({ q: "a", page: 0 });
  });
});
