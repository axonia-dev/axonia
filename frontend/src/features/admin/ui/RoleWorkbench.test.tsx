import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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
import { forceMobileViewport, restoreViewport } from "@/test/viewport";

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
  {
    id: 4,
    name: "Zed",
    description: null,
    system: false,
    permissions: [],
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

describe("the roles workbench", () => {
  it("lists every role in the rail, system roles first", () => {
    renderAt(`${BASE}/roles/2`);
    const names = screen.getAllByRole("link").map((a) => a.textContent ?? "");
    expect(names.at(0)).toContain("Owner");
    expect(names.at(1)).toContain("Auditor");
  });

  it("opens the role named in the URL", () => {
    renderAt(`${BASE}/roles/2`);
    expect(
      screen.getByRole("heading", { name: "Auditor", level: 2 }),
    ).toBeInTheDocument();
    expect(screen.getByText("Reads only.")).toBeInTheDocument();
  });

  it("marks a role holding only reads as read-only", () => {
    renderAt(`${BASE}/roles/2`);
    expect(screen.getByText("Read-only")).toBeInTheDocument();
  });

  it("names each rail row for assistive technology, which cannot see the glyph", () => {
    renderAt(`${BASE}/roles/2`);
    expect(
      screen.getByRole("link", {
        name: "Directory Admin, custom role, 1 of 20 permissions",
      }),
    ).toHaveAttribute("href", `${BASE}/roles/3`);
  });

  it("saves a permission toggle immediately", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("switch", { name: "Manage members" }));
    // The write runs through the serialized queue, so it lands a microtask later.
    await waitFor(() =>
      expect(updateMutate).toHaveBeenCalledWith({
        id: 2,
        body: {
          name: "Auditor",
          description: "Reads only.",
          permissions: ["member:read", "member:write"],
        },
      }),
    );
  });

  it("never offers a live switch on a system role", () => {
    renderAt(`${BASE}/roles/1`);
    expect(screen.getByRole("switch", { name: "View members" })).toBeDisabled();
  });

  it("draws every domain expanded, including the ones the role reaches into nothing of", () => {
    renderAt(`${BASE}/roles/2`);
    // Auditor holds only member:read. A collapsed panel is `hidden`, so reaching a switch by
    // role proves its group is both drawn and open.
    expect(
      screen.getByRole("switch", { name: "View members" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("switch", { name: "Manage directory sync" }),
    ).toBeInTheDocument();
  });

  it("draws every domain expanded for a role granting nothing", () => {
    renderAt(`${BASE}/roles/4`);
    expect(
      screen.getByRole("switch", { name: "View members" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("switch", { name: "Manage directory sync" }),
    ).toBeInTheDocument();
  });

  it("turns the group chevron when the group is collapsed", async () => {
    renderAt(`${BASE}/roles/2`);
    const trigger = screen.getByRole("button", { name: /^Members/ });
    const chevron = trigger.querySelector("svg");
    expect(chevron).not.toBeNull();
    expect(chevron?.getAttribute("class")).not.toContain("-rotate-90");
    await user.click(trigger);
    expect(chevron?.getAttribute("class")).toContain("-rotate-90");
  });

  it("opens the first role in rail order at a bare /roles", () => {
    renderAt(`${BASE}/roles`);
    expect(
      screen.getByRole("heading", { name: "Owner", level: 2 }),
    ).toBeInTheDocument();
  });

  it("counts only the permissions the matrix carries, not every code the role holds", () => {
    mockUseRoles.mockReturnValue({
      data: [
        {
          id: 7,
          name: "Creator",
          description: null,
          system: false,
          // community:create is enforced by the gate but deliberately absent from the
          // matrix, so the pane draws no switch for it and must not count it either.
          permissions: ["member:read", "community:create"],
        },
      ],
      isPending: false,
    });
    renderAt(`${BASE}/roles/7`);
    expect(screen.getByText(/of 20 permissions/)).toHaveTextContent(
      "1 of 20 permissions",
    );
    expect(
      screen.getByRole("link", {
        name: "Creator, custom role, 1 of 20 permissions",
      }),
    ).toBeInTheDocument();
  });

  it("duplicates a system role under a new name, permissions and all", async () => {
    renderAt(`${BASE}/roles/1`);
    await user.click(screen.getByRole("button", { name: "Role actions" }));
    expect(
      screen.queryByRole("menuitem", { name: "Rename" }),
    ).not.toBeInTheDocument();
    await user.click(screen.getByRole("menuitem", { name: "Duplicate" }));
    expect(createMutate).toHaveBeenCalledWith({
      name: "Owner (copy)",
      description: null,
      permissions: ["member:read", "member:write"],
    });
  });

  it("renames a custom role through the editor, prefilled from the role", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("button", { name: "Role actions" }));
    await user.click(screen.getByRole("menuitem", { name: "Rename" }));
    expect(screen.getByLabelText("Name")).toHaveValue("Auditor");
  });

  it("asks before deleting a custom role", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("button", { name: "Role actions" }));
    await user.click(screen.getByRole("menuitem", { name: "Delete" }));
    expect(screen.getByRole("alertdialog")).toHaveTextContent(
      "This permanently removes the role.",
    );
  });
});

describe("a viewer who can read roles but not write them", () => {
  beforeEach(() => {
    can.mockImplementation((permission: string) => permission === "role:read");
  });

  it("is offered no way to change or duplicate a role", () => {
    renderAt(`${BASE}/roles/2`);
    // Duplicate creates a role, so the whole menu is out of reach, not just rename and delete.
    expect(
      screen.queryByRole("button", { name: "Role actions" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("switch", { name: "View members" })).toBeDisabled();
  });
});

describe("the roles workbench below the breakpoint", () => {
  beforeEach(() => {
    forceMobileViewport();
  });
  afterEach(() => {
    restoreViewport();
  });

  it("is the rail alone, with no pane and no redirect, at a bare /roles", () => {
    renderAt(`${BASE}/roles`);
    expect(screen.getByRole("link", { name: /^Auditor/ })).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Auditor", level: 2 }),
    ).not.toBeInTheDocument();
  });

  it("is the pane alone, with a back link carrying the query string", () => {
    renderAt(`${BASE}/roles/2?q=member`);
    expect(
      screen.getByRole("heading", { name: "Auditor", level: 2 }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: /^Owner/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "All roles" })).toHaveAttribute(
      "href",
      `${BASE}/roles?q=member`,
    );
  });
});
