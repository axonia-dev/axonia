import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAccessMe } from "@/features/admin/api/use-access-me";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { RequirePermission } from "@/features/admin/ui/RequirePermission";
import { signInScopedTo, signOutOfTest } from "@/test/access-token";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));
vi.mock("@/features/admin/api/use-access-me", () => ({
  useAccessMe: vi.fn(),
}));

const mockUsePermissions = vi.mocked(usePermissions);

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  signOutOfTest();
  mockUsePermissions.mockReset();
});

describe("RequirePermission", () => {
  it("renders children when the permission is granted", () => {
    // given
    mockUsePermissions.mockReturnValue(permissionState(() => true));
    render(
      <RequirePermission permission="community_kind:write">
        <span>Admin content</span>
      </RequirePermission>,
    );

    // then
    expect(screen.getByText("Admin content")).toBeInTheDocument();
  });

  it("renders the no-access notice when permission is not granted", () => {
    // given
    mockUsePermissions.mockReturnValue(permissionState(() => false));
    render(
      <RequirePermission permission="community_kind:write">
        <span>Admin content</span>
      </RequirePermission>,
    );

    // then
    expect(screen.getByRole("alert")).toHaveTextContent("No access to this.");
    expect(screen.queryByText("Admin content")).not.toBeInTheDocument();
  });

  it("renders the loading hint while the query is pending", () => {
    // given
    mockUsePermissions.mockReturnValue(
      permissionState(() => false, { isLoading: true }),
    );
    render(
      <RequirePermission permission="community_kind:write">
        <span>Admin content</span>
      </RequirePermission>,
    );

    // then
    expect(screen.getByText("Loading…")).toBeInTheDocument();
    expect(screen.queryByText("Admin content")).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("says the permissions could not load under the page's heading, never that access is refused", async () => {
    // given
    const refetch = vi.fn(() => Promise.resolve());
    mockUsePermissions.mockReturnValue(
      permissionState(() => false, {
        isError: true,
        retry: { isFetching: false, refetch },
      }),
    );
    render(
      <RequirePermission permission="community_kind:write" title="Vocabulary">
        <span>Admin content</span>
      </RequirePermission>,
    );

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the permissions.",
    );
    expect(screen.getByRole("heading", { name: "Vocabulary" })).toBeVisible();
    expect(screen.queryByText("Admin content")).not.toBeInTheDocument();
    expect(refetch).toHaveBeenCalledOnce();
  });

  it("keeps the page on screen when a background refetch of permissions fails", async () => {
    // given - the real hook, over cached permissions whose refetch failed
    const { usePermissions: realUsePermissions } = await vi.importActual<
      typeof import("@/features/admin/api/use-permissions")
    >("@/features/admin/api/use-permissions");
    mockUsePermissions.mockImplementation(realUsePermissions);
    vi.mocked(useAccessMe).mockReturnValue({
      data: { permissions: ["member:read"] },
      isPending: false,
      isError: true,
    } as never);
    signInScopedTo("acme");

    // when
    render(
      <RequirePermission permission="member:read">
        <p>Users page</p>
      </RequirePermission>,
    );

    // then
    expect(screen.getByText("Users page")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
