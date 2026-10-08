import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { openPermissionCatalog } from "@/features/admin/domain/edition-permissions";
import { PERMISSION_CATALOG } from "@/features/admin/domain/permission-catalog";
import {
  BASE,
  can,
  createMutate,
  mockHeldPermissions,
  mockUseRoles,
  renderAt,
  updateMutate,
} from "@/features/admin/ui/RoleWorkbench.test-helpers";

// Split out of RoleWorkbench.test.tsx: pinning a second role as a ghost column and the
// differences-only filter took that file over the 300-line cap.

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

describe("comparing two roles", () => {
  it("shows the pinned role as a read-only column", () => {
    renderAt(`${BASE}/roles/2?compare=1`);
    expect(
      screen.getByRole("img", { name: "Owner grants Manage members" }),
    ).toBeInTheDocument();
  });

  it("counts the differences in the summary line", () => {
    renderAt(`${BASE}/roles/2?compare=1`);
    expect(screen.getByText("1 differs from Owner")).toBeInTheDocument();
  });

  it("names both columns, so the marks belong to something", () => {
    const { container } = renderAt(`${BASE}/roles/2?compare=1`);
    const heads = container.querySelectorAll("[data-testid='compare-head']");
    expect(Array.from(heads).map((h) => h.textContent)).toEqual([
      "Owner",
      "Auditor",
    ]);
  });

  it("reads each column under its own head: pinned mark first, then this role's switch", () => {
    const { container } = renderAt(`${BASE}/roles/2?compare=1`);

    const ghost = screen.getByRole("img", {
      name: "Owner grants Manage members",
    });
    const own = screen.getByRole("switch", { name: "Manage members" });
    expect(
      ghost.compareDocumentPosition(own) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeGreaterThan(0);

    const heads = Array.from(
      container.querySelectorAll("[data-testid='compare-head']"),
    );
    expect(heads[0]?.textContent).toBe("Owner");
    expect(heads[1]?.textContent).toBe("Auditor");
  });

  it("hides the rows both roles agree on when differences only is on", async () => {
    renderAt(`${BASE}/roles/2?compare=1`);
    expect(screen.getByText("View members")).toBeInTheDocument();
    await user.click(screen.getByRole("switch", { name: "Differences only" }));
    expect(screen.queryByText("View members")).not.toBeInTheDocument();
    expect(screen.getByText("Manage members")).toBeInTheDocument();
  });

  it("draws a group only the pinned role reaches into, which is a difference to read", () => {
    renderAt(`${BASE}/roles/2?compare=3`);
    // Auditor holds nothing under Directory sync and Directory Admin holds scim:write. A collapsed
    // panel is `hidden`, so reaching the switch by role proves the group is open.
    expect(
      screen.getByRole("switch", { name: "Manage directory sync" }),
    ).toBeInTheDocument();
  });

  it("offers no differences-only control when nothing is pinned", () => {
    renderAt(`${BASE}/roles/2`);
    expect(
      screen.queryByRole("switch", { name: "Differences only" }),
    ).not.toBeInTheDocument();
  });

  it("keeps the pin when another role is opened", async () => {
    renderAt(`${BASE}/roles/2?compare=1`);
    await user.click(screen.getByRole("link", { name: /^Directory Admin,/ }));
    expect(screen.getByText(/differs? from Owner/)).toBeInTheDocument();
  });

  it("clears the pin when the pinned role is itself opened", () => {
    renderAt(`${BASE}/roles/1?compare=1`);
    expect(screen.queryByText(/differs? from/)).not.toBeInTheDocument();
  });
});

describe("the differences-only remainder", () => {
  it("states what it removed, and offers the way back", async () => {
    renderAt(`${BASE}/roles/2?compare=1`);
    await user.click(screen.getByRole("switch", { name: "Differences only" }));

    expect(
      screen.getByText("The other 19 permissions are identical in both roles."),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Show them" }));

    expect(
      screen.queryByText(
        "The other 19 permissions are identical in both roles.",
      ),
    ).not.toBeInTheDocument();
    expect(screen.getByText("View members")).toBeInTheDocument();
  });

  it("does not survive the comparison it filtered: a fresh pin opens unfiltered", async () => {
    renderAt(`${BASE}/roles/2?compare=1`);
    await user.click(screen.getByRole("switch", { name: "Differences only" }));
    expect(
      screen.getByRole("switch", { name: "Differences only" }),
    ).toBeChecked();

    await user.click(
      screen.getByRole("button", { name: "Stop comparing with Owner" }),
    );
    await user.click(screen.getByRole("button", { name: /Compare with/ }));
    await user.click(screen.getByRole("option", { name: "Directory Admin" }));

    expect(
      screen.getByRole("switch", { name: "Differences only" }),
    ).not.toBeChecked();
    // A row the two agree on: Auditor and Directory Admin both lack it, so differences-only would
    // have hidden it. "View members" would not prove the reset - the two differ on it.
    expect(screen.getByText("Manage members")).toBeInTheDocument();
  });

  it("uses singular grammar for exactly one agreeing permission", async () => {
    const codes = PERMISSION_CATALOG.map((p) => p.code);
    const distinguishing = codes.slice(0, -1);
    const half = Math.ceil(distinguishing.length / 2);
    const roleA = {
      id: 5,
      name: "Alpha",
      description: null,
      system: false,
      permissions: distinguishing.slice(0, half),
    };
    const roleB = {
      id: 6,
      name: "Beta",
      description: null,
      system: false,
      permissions: distinguishing.slice(half),
    };
    mockUseRoles.mockReturnValue({ data: [roleA, roleB], isPending: false });

    renderAt(`${BASE}/roles/5?compare=6`);
    await user.click(screen.getByRole("switch", { name: "Differences only" }));

    expect(
      screen.getByText("The other 1 permission is identical in both roles."),
    ).toBeInTheDocument();
  });
});
