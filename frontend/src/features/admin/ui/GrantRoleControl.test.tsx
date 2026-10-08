import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useGrantRole } from "@/features/admin/api/use-grant-role";
import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { useRoles } from "@/features/admin/api/use-roles";
import { GrantRoleControl } from "@/features/admin/ui/GrantRoleControl";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/admin/api/use-roles", () => ({ useRoles: vi.fn() }));
vi.mock("@/features/admin/api/use-grant-role", () => ({
  useGrantRole: vi.fn(),
}));
vi.mock("@/features/admin/api/use-held-permissions", () => ({
  useHeldPermissions: vi.fn(),
}));

const mockUseRoles = vi.mocked(useRoles);
const mockUseGrantRole = vi.mocked(useGrantRole);
const mockUseHeldPermissions = vi.mocked(useHeldPermissions);

const roles = [{ id: 10, name: "ADMIN", permissions: [] }];

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseRoles.mockReturnValue({ data: roles } as never);
  mockUseGrantRole.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  mockUseHeldPermissions.mockReturnValue(new Set(["role:write"]));
});

describe("GrantRoleControl", () => {
  it("grants the chosen role Organization-wide with no scope picker", async () => {
    // given
    const mutate = vi.fn();
    mockUseGrantRole.mockReturnValue({ mutate, isPending: false } as never);
    render(<GrantRoleControl userId="u1" />);

    // when
    await user.click(screen.getByRole("button", { name: /choose a role/i }));
    await user.click(screen.getByRole("option", { name: "ADMIN" }));
    await user.click(screen.getByRole("button", { name: /grant role/i }));

    // then
    expect(
      screen.queryByRole("button", { name: /scope/i }),
    ).not.toBeInTheDocument();
    expect(mutate.mock.calls[0]?.[0]).toEqual({
      subjectType: "USER",
      subjectId: "u1",
      roleId: 10,
    });
  });

  it("says which role is already held when the grant conflicts", async () => {
    // given - the backend refuses a role the subject already holds with 409 role_already_bound
    const mutate = vi.fn(
      (_body: unknown, opts?: { onError?: (err: unknown) => void }): void => {
        opts?.onError?.(
          new ApiError(
            409,
            "Already holds the ADMIN role.",
            "role_already_bound",
          ),
        );
      },
    );
    mockUseGrantRole.mockReturnValue({ mutate, isPending: false } as never);
    render(<GrantRoleControl userId="u1" />);

    // when
    await user.click(screen.getByRole("button", { name: /choose a role/i }));
    await user.click(screen.getByRole("option", { name: "ADMIN" }));
    await user.click(screen.getByRole("button", { name: /grant role/i }));

    // then - the server's own wording, not a generic failure
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Already holds the ADMIN role.",
    );
  });

  it("disables the grant button until a role is chosen", () => {
    // given / when
    render(<GrantRoleControl userId="u1" />);

    // then
    expect(screen.getByRole("button", { name: /grant role/i })).toBeDisabled();
  });

  it("lists a role beyond the caller's reach as an option that cannot be picked", async () => {
    // given
    mockUseRoles.mockReturnValue({
      data: [
        { id: 1, name: "Auditor", permissions: ["member:read"] },
        { id: 2, name: "Owner", permissions: ["organization:export"] },
      ],
    } as never);
    mockUseHeldPermissions.mockReturnValue(
      new Set(["role:write", "member:read"]),
    );
    render(<GrantRoleControl userId="u1" />);

    // when
    await user.click(screen.getByRole("button", { name: /Role/ }));

    // then - within reach: listed and pickable
    expect(screen.getByRole("option", { name: /Auditor/ })).not.toHaveAttribute(
      "aria-disabled",
      "true",
    );
    // then - beyond reach: listed, but marked disabled and explained
    expect(screen.getByRole("option", { name: /Owner/ })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(
      screen.getByText("Reaches beyond what this account holds."),
    ).toBeInTheDocument();
  });
});

describe("GrantRoleControl (roles not loaded)", () => {
  it("says the roles are loading, and opens no role list until they are", () => {
    // given
    mockUseRoles.mockReturnValue({
      data: undefined,
      errorUpdateCount: 0,
    } as never);

    // when
    render(<GrantRoleControl userId="u1" />);

    // then
    expect(
      screen.getByRole("button", { name: "Loading… Role" }),
    ).toBeDisabled();
  });

  it("says the roles could not load, in place of the role list", () => {
    // given
    mockUseRoles.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn(),
    } as never);

    // when
    render(<GrantRoleControl userId="u1" />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the roles.",
    );
    expect(
      screen.queryByRole("button", { name: /choose a role/i }),
    ).not.toBeInTheDocument();
  });
});
