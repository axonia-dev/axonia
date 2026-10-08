import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useBindGroup } from "@/features/admin/api/use-bind-group";
import { useConfirmGroup } from "@/features/admin/api/use-confirm-group";
import { useHeldPermissions } from "@/features/admin/api/use-held-permissions";
import { useIdpGroups } from "@/features/admin/api/use-idp-groups";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useRoles } from "@/features/admin/api/use-roles";
import { useUnbindGroup } from "@/features/admin/api/use-unbind-group";
import { GroupAccessPage } from "@/features/admin/ui/GroupAccessPage";
import { ApiError } from "@/lib/api-client";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/api/use-idp-groups", () => ({
  useIdpGroups: vi.fn(),
}));

vi.mock("@/features/admin/api/use-held-permissions", () => ({
  useHeldPermissions: vi.fn(),
}));

vi.mock("@/features/admin/api/use-roles", () => ({
  useRoles: vi.fn(),
  useRoleAssignees: vi.fn(),
}));

vi.mock("@/features/admin/api/use-bind-group", () => ({
  useBindGroup: vi.fn(),
}));

vi.mock("@/features/admin/api/use-unbind-group", () => ({
  useUnbindGroup: vi.fn(),
}));

vi.mock("@/features/admin/api/use-confirm-group", () => ({
  useConfirmGroup: vi.fn(),
}));

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

const mockUseIdpGroups = vi.mocked(useIdpGroups);
const mockUseRoles = vi.mocked(useRoles);
const mockUseBindGroup = vi.mocked(useBindGroup);
const mockUseUnbindGroup = vi.mocked(useUnbindGroup);
const mockUseConfirmGroup = vi.mocked(useConfirmGroup);
const mockUsePermissions = vi.mocked(usePermissions);
const mockUseHeldPermissions = vi.mocked(useHeldPermissions);

const roles = [
  { id: 10, name: "ADMIN", permissions: [] },
  { id: 20, name: "MEMBER", permissions: [] },
];

const binding = {
  id: 1,
  subjectType: "GROUP",
  subjectId: "grp-eng",
  roleId: 10,
  subjectLabel: null,
};

const idpGroupsResponse = {
  source: "SCIM",
  groups: [
    {
      groupId: "grp-eng",
      displayName: "Engineering",
      inDirectory: true,
      awaitingConfirmation: false,
      bindings: [binding],
    },
    {
      groupId: "grp-qa",
      displayName: "QA",
      inDirectory: false,
      awaitingConfirmation: false,
      bindings: [],
    },
  ],
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  mockUseIdpGroups.mockReturnValue({
    data: idpGroupsResponse,
    isPending: false,
  } as never);
  mockUseRoles.mockReturnValue({ data: roles, isPending: false } as never);
  mockUseBindGroup.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  mockUseUnbindGroup.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  mockUseConfirmGroup.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  mockUseHeldPermissions.mockReturnValue(new Set(["role:write"]));
});

describe("GroupAccessPage", () => {
  it("shows the resolved source in the add-binding panel", () => {
    render(<GroupAccessPage />);
    const panel = screen.getByText("Add a binding").closest("section")!;
    expect(within(panel).getByText(/scim directory/i)).toBeInTheDocument();
  });

  it("shows bound groups with their display names", () => {
    render(<GroupAccessPage />);
    expect(screen.getByText("Engineering")).toBeInTheDocument();
  });

  it("does not show a group with no bindings and no directory presence", () => {
    render(<GroupAccessPage />);
    expect(screen.queryByText("QA")).not.toBeInTheDocument();
  });

  it("shows binding role names resolved from useRoles", () => {
    render(<GroupAccessPage />);
    const bindingsList = screen.getByRole("list", {
      name: "bindings for Engineering",
    });
    expect(within(bindingsList).getByText("ADMIN")).toBeInTheDocument();
  });

  it("shows a provenance pill sourced from the directory for a directory-backed binding", () => {
    render(<GroupAccessPage />);
    const engCard = screen.getByText("Engineering").closest("li")!;
    expect(within(engCard).getByText(/scim directory/i)).toBeInTheDocument();
  });

  it("calls useUnbindGroup.mutate with the binding id when Unbind is clicked", async () => {
    const mutate = vi.fn();
    mockUseUnbindGroup.mockReturnValue({ mutate, isPending: false } as never);
    render(<GroupAccessPage />);

    await user.click(screen.getByRole("button", { name: /unbind/i }));

    expect(mutate.mock.calls[0]?.[0]).toBe(1);
  });

  it("surfaces the reason when an unbind is refused by the last-admin guard", async () => {
    const mutate = vi.fn(
      (_id: number, opts?: { onError?: (err: unknown) => void }) => {
        opts?.onError?.(
          new ApiError(
            409,
            "At least one group must keep role:write so roles stay manageable.",
          ),
        );
      },
    );
    mockUseUnbindGroup.mockReturnValue({ mutate, isPending: false } as never);
    render(<GroupAccessPage />);

    await user.click(screen.getByRole("button", { name: /unbind/i }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "roles stay manageable",
    );
  });

  it("binds a typed group value even when it is not in the directory", async () => {
    const mutate = vi.fn();
    mockUseBindGroup.mockReturnValue({ mutate, isPending: false } as never);

    render(<GroupAccessPage />);
    await user.type(
      screen.getByRole("combobox", { name: /group/i }),
      "data-leads",
    );
    await user.click(screen.getByRole("button", { name: /choose a role/i }));
    await user.click(await screen.findByRole("option", { name: "ADMIN" }));
    await user.click(screen.getByRole("button", { name: /^bind$/i }));

    expect(mutate).toHaveBeenCalledWith(
      { groupId: "data-leads", displayName: "data-leads", roleId: 10 },
      expect.anything(),
    );
  });

  it("binds a directory-suggested group by its id, not its display label", async () => {
    const mutate = vi.fn();
    mockUseBindGroup.mockReturnValue({ mutate, isPending: false } as never);

    render(<GroupAccessPage />);
    await user.click(screen.getByRole("combobox", { name: /group/i }));
    await user.click(
      await screen.findByRole("option", { name: /^Engineering/ }),
    );
    await user.click(
      await screen.findByRole("button", { name: /choose a role/i }),
    );
    await user.click(await screen.findByRole("option", { name: "ADMIN" }));
    await user.click(screen.getByRole("button", { name: /^bind$/i }));

    expect(mutate).toHaveBeenCalledWith(
      { groupId: "grp-eng", displayName: "Engineering", roleId: 10 },
      expect.anything(),
    );
  });

  it("has no scope picker anywhere on the page", () => {
    render(<GroupAccessPage />);
    expect(
      screen.queryByRole("button", { name: /scope/i }),
    ).not.toBeInTheDocument();
  });

  it("hides Unbind and the Add a binding panel when user lacks role:assign", () => {
    mockUsePermissions.mockReturnValue(
      permissionState((p: string) => p === "role:read"),
    );
    render(<GroupAccessPage />);

    expect(
      screen.queryByRole("button", { name: /unbind/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^bind$/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("combobox", { name: /group/i }),
    ).not.toBeInTheDocument();
  });

  it("blocks access when user lacks role:read", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => false));
    render(<GroupAccessPage />);

    expect(screen.getByRole("alert")).toHaveTextContent("No access to this.");
    expect(screen.queryByText("Engineering")).not.toBeInTheDocument();
  });

  it("shows empty state when no groups are bound", () => {
    mockUseIdpGroups.mockReturnValue({
      data: { source: "NONE", groups: [] },
      isPending: false,
    } as never);
    render(<GroupAccessPage />);

    expect(screen.getByText("No bindings yet")).toBeInTheDocument();
  });

  it("shows loading state while groups are pending", () => {
    mockUseIdpGroups.mockReturnValue({
      data: undefined,
      isPending: true,
    } as never);
    render(<GroupAccessPage />);

    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });
});
