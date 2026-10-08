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

// Split out of RoleWorkbench.test.tsx: the search contract took that file over the
// 300-line cap, and frontend test cost is per file, so this is a considered third file rather
// than a fourth.

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
    id: 1,
    name: "Owner",
    description: null,
    system: true,
    permissions: ["member:read", "member:write"],
  },
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

describe("searching", () => {
  it("keeps only the roles that grant a matching permission", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.type(screen.getByRole("searchbox"), "directory");
    expect(
      screen.getByRole("link", { name: /^Directory Admin,/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /^Auditor,/ }),
    ).not.toBeInTheDocument();
  });

  it("still matches a role by its own name", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.type(screen.getByRole("searchbox"), "Audit");
    expect(screen.getByRole("link", { name: /^Auditor,/ })).toBeInTheDocument();
  });

  it("says how many roles grant the matching permission", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.type(screen.getByRole("searchbox"), "directory");
    expect(
      screen.getByText(
        (_, element) =>
          element?.tagName.toLowerCase() === "p" &&
          (element.textContent ?? "").includes("1 of 3 roles grant"),
      ),
    ).toBeInTheDocument();
  });

  it("states the roles it removed rather than dropping them silently", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.type(screen.getByRole("searchbox"), "directory");
    expect(
      screen.getByText("2 roles grant nothing matching."),
    ).toBeInTheDocument();
  });

  it("uses singular grammar for exactly one role hidden by the search", async () => {
    mockUseRoles.mockReturnValue({
      data: [
        {
          id: 5,
          name: "Alpha",
          description: null,
          system: false,
          permissions: ["scim:write"],
        },
        {
          id: 6,
          name: "Beta",
          description: null,
          system: false,
          permissions: ["member:read"],
        },
      ],
      isPending: false,
    });
    renderAt(`${BASE}/roles/5`);
    await user.type(screen.getByRole("searchbox"), "directory");
    expect(
      screen.getByText("1 role grants nothing matching."),
    ).toBeInTheDocument();
  });

  it("reaches the matching permission's row even in a role that lacks it", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.type(screen.getByRole("searchbox"), "directory");
    expect(
      screen.getByRole("switch", { name: "Manage directory sync" }),
    ).toBeInTheDocument();
  });

  it("marks the matched run in a permission label, keeping the switch's full name", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.type(screen.getByRole("searchbox"), "directory");

    expect(
      screen.getByRole("switch", { name: "Manage directory sync" }),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText("directory", { selector: "mark" }).length,
    ).toBeGreaterThan(0);
    expect(
      screen.queryAllByText("Manage members", { selector: "mark" }),
    ).toHaveLength(0);
  });

  it("carries the query on every row, so a search is shareable", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.type(screen.getByRole("searchbox"), "directory");
    expect(
      screen.getByRole("link", { name: /^Directory Admin,/ }),
    ).toHaveAttribute("href", `${BASE}/roles/3?q=directory`);
  });
});
