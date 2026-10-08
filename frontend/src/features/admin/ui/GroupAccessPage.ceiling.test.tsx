import { render, screen } from "@testing-library/react";
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
import { permissionState } from "@/test/permissions";

// Split out of GroupAccessPage.test.tsx (which was pushing oxlint's max-lines): the
// privilege ceiling is its own contract - a bound role or a bind-picker option
// beyond the caller's reach stays present but refused, never removed.
vi.mock("@/features/admin/api/use-idp-groups", () => ({
  useIdpGroups: vi.fn(),
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

vi.mock("@/features/admin/api/use-held-permissions", () => ({
  useHeldPermissions: vi.fn(),
}));

const mockUseIdpGroups = vi.mocked(useIdpGroups);
const mockUseRoles = vi.mocked(useRoles);
const mockUseHeldPermissions = vi.mocked(useHeldPermissions);

const roles = [
  { id: 10, name: "ADMIN", permissions: ["member:read"] },
  { id: 30, name: "Owner", permissions: ["organization:export"] },
];

const binding = {
  id: 1,
  subjectType: "GROUP",
  subjectId: "grp-eng",
  roleId: 30,
  subjectLabel: null,
};

function givenGroups(roleId: number) {
  mockUseIdpGroups.mockReturnValue({
    data: {
      source: "SCIM",
      groups: [
        {
          groupId: "grp-eng",
          displayName: "Engineering",
          inDirectory: true,
          awaitingConfirmation: false,
          bindings: [{ ...binding, roleId }],
        },
      ],
    },
    isPending: false,
  } as never);
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(usePermissions).mockReturnValue(permissionState(() => true));
  mockUseRoles.mockReturnValue({ data: roles, isPending: false } as never);
  vi.mocked(useBindGroup).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useUnbindGroup).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useConfirmGroup).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  mockUseHeldPermissions.mockReturnValue(
    new Set(["role:write", "member:read"]),
  );
  givenGroups(30);
});

describe("GroupAccessPage ceiling", () => {
  it("does not offer to unbind a role that reaches past the caller", () => {
    render(<GroupAccessPage />);

    const unbind = screen.getByRole("button", { name: "Unbind" });
    expect(unbind).toBeDisabled();
    expect(
      screen.getByText("Reaches beyond what this account holds."),
    ).toBeInTheDocument();
    // The association reaches the paragraph, rather than naming an id nothing rendered.
    expect(
      document.getElementById(unbind.getAttribute("aria-describedby") ?? ""),
    ).toHaveTextContent("Reaches beyond what this account holds.");
  });

  it("disables unbind for a role the catalog cannot resolve, without claiming the ceiling is why", () => {
    givenGroups(999);

    render(<GroupAccessPage />);

    const unbind = screen.getByRole("button", { name: "Unbind" });
    expect(unbind).toBeDisabled();
    expect(screen.getByText("999")).toBeInTheDocument();
    expect(
      screen.queryByText("Reaches beyond what this account holds."),
    ).not.toBeInTheDocument();
    // No paragraph renders here, so the button must carry no association to one either.
    expect(unbind).not.toHaveAttribute("aria-describedby");
  });

  it("offers a plain, enabled unbind when the bound role is within reach", () => {
    givenGroups(10);

    render(<GroupAccessPage />);

    expect(screen.getByRole("button", { name: "Unbind" })).toBeEnabled();
    expect(
      screen.queryByText("Reaches beyond what this account holds."),
    ).not.toBeInTheDocument();
  });

  it("lists a role beyond reach in the bind picker as unchoosable", async () => {
    render(<GroupAccessPage />);
    await user.click(screen.getByRole("button", { name: /Role/ }));

    expect(screen.getByRole("option", { name: /Owner/ })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("lists a role within reach in the bind picker as choosable", async () => {
    render(<GroupAccessPage />);
    await user.click(screen.getByRole("button", { name: /Role/ }));

    expect(screen.getByRole("option", { name: "ADMIN" })).not.toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });
});
