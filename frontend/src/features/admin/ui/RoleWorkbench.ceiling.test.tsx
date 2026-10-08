import { screen, within } from "@testing-library/react";
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

// Split out of RoleWorkbench.test.tsx: the privilege ceiling is its own contract -
// a permission the caller does not hold is drawn locked, and the pane states how many are.

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

const CATALOG = openPermissionCatalog.entries;
const ALL_CODES = new Set(CATALOG.map((p) => p.code));

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
    name: "Payments",
    description: "Collects fees.",
    system: false,
    permissions: ["organization:export"],
  },
];

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  can.mockImplementation(() => true);
  updateMutate.mockResolvedValue(undefined);
  mockUseRoles.mockReturnValue({ data: ROLES, isPending: false });
});

/** The row for one permission label: the nearest ancestor of the label that also contains its
 *  switch. Scoping to it is what lets a test read one row's lock note when, with a caller
 *  holding only a couple of codes, most of the other twenty-odd rows are locked too. */
function rowFor(label: string): HTMLElement {
  const text = screen.getByText(label);
  const control = screen.getByRole("switch", {
    name: (name) => name.includes(label),
  });
  let node: HTMLElement | null = text;
  while (node !== null && !node.contains(control)) {
    node = node.parentElement;
  }
  if (node === null) {
    throw new Error(`no row found containing both "${label}" and its switch`);
  }
  return node;
}

describe("the privilege ceiling", () => {
  it("draws a permission the caller does not hold as locked, and says why", () => {
    mockHeldPermissions.mockReturnValue(new Set(["role:write"]));
    renderAt(`${BASE}/roles/2`);

    const row = rowFor("View members");
    expect(
      within(row).getByText("Requires holding this capability."),
    ).toBeInTheDocument();
    const control = screen.getByRole("switch", { name: /View members/ });
    expect(control).toBeDisabled();
    expect(control).toHaveAccessibleDescription(
      /Requires holding this capability/,
    );
  });

  it("leaves a permission the caller holds movable", () => {
    mockHeldPermissions.mockReturnValue(new Set(["role:write", "member:read"]));
    renderAt(`${BASE}/roles/2`);

    const row = rowFor("View members");
    const control = screen.getByRole("switch", { name: /View members/ });
    expect(control).toBeEnabled();
    expect(
      within(row).queryByText("Requires holding this capability."),
    ).not.toBeInTheDocument();
    expect(control).not.toHaveAccessibleDescription(
      /Requires holding this capability/,
    );
  });

  it("states the locked count once above the domains", async () => {
    mockHeldPermissions.mockReturnValue(new Set(["role:write"]));
    renderAt(`${BASE}/roles/2`);

    const locked = CATALOG.filter((p) => p.code !== "role:write").length;
    expect(
      await screen.findByText(
        `${locked} of ${CATALOG.length} capabilities are locked. Changing one requires already holding it.`,
      ),
    ).toBeInTheDocument();
  });

  it("says nothing when the caller holds the whole applicable catalog", () => {
    mockHeldPermissions.mockReturnValue(ALL_CODES);
    renderAt(`${BASE}/roles/2`);

    expect(
      screen.queryByText(/capabilities are locked/),
    ).not.toBeInTheDocument();
  });

  it("says nothing on a system role, where every switch is already inert for a different reason", () => {
    mockHeldPermissions.mockReturnValue(new Set(["role:write"]));
    renderAt(`${BASE}/roles/1`);

    // Positive anchor: the pane drew its rows, it just says nothing about the ceiling on them.
    expect(
      screen.getByRole("switch", { name: /View members/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("Requires holding this capability."),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/capabilities are locked/),
    ).not.toBeInTheDocument();
  });

  it("says nothing for a reader with no role:write either, the other way a row can be inert", () => {
    can.mockImplementation((permission: string) => permission === "role:read");
    mockHeldPermissions.mockReturnValue(new Set(["role:write"]));
    renderAt(`${BASE}/roles/2`);

    expect(
      screen.getByRole("switch", { name: /View members/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("Requires holding this capability."),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText(/capabilities are locked/),
    ).not.toBeInTheDocument();
  });
});

describe("the same ceiling on delete and duplicate", () => {
  it("offers neither delete nor duplicate on a role that reaches past the caller", async () => {
    mockHeldPermissions.mockReturnValue(new Set(["role:write"]));
    renderAt(`${BASE}/roles/3`);

    await user.click(screen.getByRole("button", { name: "Role actions" }));

    expect(
      screen.getByRole("menuitem", { name: "Rename" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: /Delete/ })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(screen.getByRole("menuitem", { name: /Duplicate/ })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
    expect(
      screen.getByText("Reaches beyond what this account holds."),
    ).toBeInTheDocument();
    // The reason is a note, not a disabled command: a MenuItem would still join the
    // collection and announce itself to assistive tech as an unavailable action.
    expect(
      screen.queryByRole("menuitem", { name: /Reaches beyond/ }),
    ).not.toBeInTheDocument();
  });

  it("offers both delete and duplicate on a role fully within reach", async () => {
    mockHeldPermissions.mockReturnValue(new Set(["role:write", "member:read"]));
    renderAt(`${BASE}/roles/2`);

    await user.click(screen.getByRole("button", { name: "Role actions" }));

    expect(
      screen.getByRole("menuitem", { name: /Delete/ }),
    ).not.toHaveAttribute("aria-disabled");
    expect(
      screen.getByRole("menuitem", { name: /Duplicate/ }),
    ).not.toHaveAttribute("aria-disabled");
    expect(
      screen.queryByText("Reaches beyond what this account holds."),
    ).not.toBeInTheDocument();
  });

  it("leaves rename enabled on a role that reaches past the caller", async () => {
    mockHeldPermissions.mockReturnValue(new Set(["role:write"]));
    renderAt(`${BASE}/roles/3`);

    await user.click(screen.getByRole("button", { name: "Role actions" }));

    const rename = screen.getByRole("menuitem", { name: "Rename" });
    expect(rename).not.toHaveAttribute("aria-disabled");
    await user.click(rename);
    expect(screen.getByLabelText("Name")).toHaveValue("Payments");
  });
});
