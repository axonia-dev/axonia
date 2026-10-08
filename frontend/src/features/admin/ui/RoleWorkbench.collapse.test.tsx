import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { openPermissionCatalog } from "@/features/admin/domain/edition-permissions";
import {
  BASE,
  can,
  createMutate,
  mockHeldPermissions,
  mockUseRoles,
  renderAt,
  updateMutate,
} from "@/features/admin/ui/RoleWorkbench.test-helpers";

// A fourth file rather than more of RoleWorkbench.test.tsx: that one is already within a
// couple of dozen lines of the 300-line cap, and this contract - one control governing every
// domain at once - stands on its own.

vi.mock("@/features/admin/api/use-roles", () => ({
  useRoles: () => mockUseRoles() as never,
  useRoleAssignees: () => ({ data: [], isPending: false }),
}));
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: () => ({ can, isLoading: false }),
}));
vi.mock("@/features/admin/api/use-held-permissions", () => ({
  useHeldPermissions: () => mockHeldPermissions() as never,
}));
vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: () => ({
    data: { id: "o", name: "Org", kind: "ENTERPRISE" },
    isPending: false,
  }),
}));
vi.mock("@/features/admin/api/use-role-mutations", () => ({
  useCreateRole: () => ({ mutate: createMutate, isPending: false }),
  useUpdateRole: () => ({
    mutate: updateMutate,
    mutateAsync: updateMutate,
    isPending: false,
  }),
  useDeleteRole: () => ({ mutate: vi.fn(), isPending: false }),
}));

const ROLES = [
  {
    id: 2,
    name: "Auditor",
    description: "Reads only.",
    system: false,
    permissions: ["member:read"],
  },
  {
    id: 3,
    name: "Directory Admin",
    description: null,
    system: false,
    permissions: ["scim:write"],
  },
];

const HELD_ALL = new Set(openPermissionCatalog.entries.map((p) => p.code));

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  can.mockImplementation(() => true);
  updateMutate.mockResolvedValue(undefined);
  mockUseRoles.mockReturnValue({ data: ROLES, isPending: false });
  mockHeldPermissions.mockReturnValue(HELD_ALL);
});

// A collapsed panel is `hidden`, so a switch reachable by role proves its domain is open.
describe("collapsing every domain at once", () => {
  it("shuts every domain, leaving the group headings", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("button", { name: "Collapse all" }));
    expect(
      screen.queryByRole("switch", { name: "View members" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /^Members/ }),
    ).toBeInTheDocument();
  });

  it("offers the opposite act once every domain is shut", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("button", { name: "Collapse all" }));
    await user.click(screen.getByRole("button", { name: "Expand all" }));
    expect(
      screen.getByRole("switch", { name: "View members" }),
    ).toBeInTheDocument();
  });

  it("offers to collapse again as soon as one domain is opened by hand", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("button", { name: "Collapse all" }));
    await user.click(screen.getByRole("button", { name: /^Members/ }));
    expect(
      screen.getByRole("button", { name: "Collapse all" }),
    ).toBeInTheDocument();
  });

  it("holds the collapse across roles, so domain tallies read down the rail", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("button", { name: "Collapse all" }));
    await user.click(screen.getByRole("link", { name: /^Directory Admin,/ }));
    expect(
      screen.getByRole("heading", { name: "Directory Admin", level: 2 }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("switch", { name: "View members" }),
    ).not.toBeInTheDocument();
  });

  it("opens a shut domain the search matched, rather than hiding the match", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("button", { name: "Collapse all" }));
    await user.type(screen.getByRole("searchbox"), "directory");
    expect(
      screen.getByRole("switch", { name: "Manage directory sync" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("switch", { name: "View members" }),
    ).not.toBeInTheDocument();
  });
});
