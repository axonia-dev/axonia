import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
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
import { WITH_AUDIT } from "@/test/edition-fixtures";

// The page shell around the workbench: the refused save, the query parameter it clears, the
// load it holds the heading through, and the three dialog callbacks. Split from
// RoleWorkbench.test.tsx, which is at the 300-line cap, and the toast mock below cannot be
// shared through the helper module either way, since vitest hoists vi.mock per file.

vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));
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

function deferred() {
  let reject!: (reason: unknown) => void;
  const promise = new Promise<void>((_res, rej) => {
    reject = rej;
  });
  return { promise, reject };
}

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

describe("a permission save the backend refuses", () => {
  it("raises the refusal, and lets the write queued behind it through", async () => {
    // The backend enforces a ceiling: a role may not be granted a permission its author
    // does not hold. The first toggle is held open so the second is genuinely queued behind
    // it, which is the case that would stall if the queue stopped draining on a rejection.
    const first = deferred();
    updateMutate.mockImplementationOnce(() => first.promise);
    renderAt(`${BASE}/roles/2`);

    await user.click(screen.getByRole("switch", { name: "Manage members" }));
    await user.click(screen.getByRole("switch", { name: "View members" }));
    expect(updateMutate).toHaveBeenCalledTimes(1);

    first.reject(new Error("Manage members is above your own ceiling."));

    await waitFor(() =>
      expect(notifyError).toHaveBeenCalledWith(
        "Manage members is above your own ceiling.",
      ),
    );
    await waitFor(() =>
      expect(updateMutate).toHaveBeenNthCalledWith(2, {
        id: 2,
        body: {
          name: "Auditor",
          description: "Reads only.",
          permissions: [],
        },
      }),
    );
  });

  it("falls back to a plain sentence when the refusal carries no message", async () => {
    updateMutate.mockRejectedValueOnce("refused");
    renderAt(`${BASE}/roles/2`);

    await user.click(screen.getByRole("switch", { name: "Manage members" }));

    await waitFor(() =>
      expect(notifyError).toHaveBeenCalledWith("Could not save the role."),
    );
  });
});

describe("clearing a query parameter", () => {
  it("takes the pin out of the URL rather than leaving it empty", async () => {
    renderAt(`${BASE}/roles/2?compare=1`);
    expect(screen.getByText(/differs? from Owner/)).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "Stop comparing with Owner" }),
    );

    // Every rail row carries the query string, so the row's href is where an emptied
    // parameter would show up as a trailing "?compare=".
    expect(
      screen.getByRole("link", { name: /^Directory Admin,/ }),
    ).toHaveAttribute("href", `${BASE}/roles/3`);
  });
});

describe("the roles page while its data is loading", () => {
  it("holds the heading and says so, rather than popping the title in later", () => {
    mockUseRoles.mockReturnValue({ data: undefined, isPending: true });
    renderAt(`${BASE}/roles/2`);

    expect(
      screen.getByRole("heading", { name: "Roles", level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByText("Loading…")).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Auditor", level: 2 }),
    ).not.toBeInTheDocument();
  });
});

describe("the page's dialogs", () => {
  it("opens an empty editor from the page header's New role button", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("button", { name: "New role" }));

    expect(screen.getByLabelText("Name")).toHaveValue("");
  });

  it("closes the editor again on Cancel", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("button", { name: "New role" }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    await waitFor(() =>
      expect(screen.queryByLabelText("Name")).not.toBeInTheDocument(),
    );
  });

  it("closes the delete confirmation again on Cancel", async () => {
    renderAt(`${BASE}/roles/2`);
    await user.click(screen.getByRole("button", { name: "Role actions" }));
    await user.click(screen.getByRole("menuitem", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    await waitFor(() =>
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument(),
    );
  });
});

describe("a role id that no longer resolves", () => {
  it("opens the first role rather than stranding the reader on a blank pane", () => {
    renderAt(`${BASE}/roles/99`);

    expect(
      screen.getByRole("heading", { name: "Owner", level: 2 }),
    ).toBeInTheDocument();
  });

  it("says the pane is empty when the search leaves no role to fall back on", () => {
    renderAt(`${BASE}/roles/99?q=nothing-matches-this`);

    expect(screen.getByText("No role is open.")).toBeInTheDocument();
  });
});

describe("a reader who cannot write roles", () => {
  it("is offered no way to create one from the page header", () => {
    can.mockImplementation((permission: string) => permission === "role:read");
    renderAt(`${BASE}/roles/2`);

    expect(
      screen.getByRole("heading", { name: "Roles", level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "New role" }),
    ).not.toBeInTheDocument();
  });
});

describe("an edition that contributes a permission group", () => {
  it("draws the group, its toggle and its permission in the role's count", () => {
    // given
    mockUseRoles.mockReturnValue({
      data: [
        {
          id: 2,
          name: "Auditor",
          description: "Reads only.",
          system: false,
          permissions: ["member:read", "audit:read"],
        },
      ],
      isPending: false,
    });
    const total = openPermissionCatalog.entries.length + 1;

    // when
    renderAt(`${BASE}/roles/2`, WITH_AUDIT);

    // then
    expect(screen.getByRole("button", { name: /^Audit/ })).toBeInTheDocument();
    expect(
      screen.getByRole("switch", { name: "View the audit trail" }),
    ).toBeChecked();
    expect(
      screen.getByText(`of ${total} permissions`, { exact: false }),
    ).toHaveTextContent(`2 of ${total} permissions`);
  });
});
