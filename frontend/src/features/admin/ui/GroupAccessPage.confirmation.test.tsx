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
const mockUseConfirmGroup = vi.mocked(useConfirmGroup);
const mockUsePermissions = vi.mocked(usePermissions);
const mockUseHeldPermissions = vi.mocked(useHeldPermissions);

const binding = {
  id: 1,
  subjectType: "GROUP",
  subjectId: "grp-eng",
  roleId: 10,
  subjectLabel: null,
};

function groupsResponse(
  awaitingConfirmation: boolean,
  bindings: (typeof binding)[] = [binding],
) {
  return {
    source: "SCIM",
    groups: [
      {
        groupId: "grp-eng",
        displayName: "Engineering",
        inDirectory: true,
        awaitingConfirmation,
        bindings,
      },
    ],
  };
}

function givenGroups(
  awaitingConfirmation: boolean,
  bindings?: (typeof binding)[],
) {
  mockUseIdpGroups.mockReturnValue({
    data: groupsResponse(awaitingConfirmation, bindings),
    isPending: false,
  } as never);
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  mockUseConfirmGroup.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useRoles).mockReturnValue({
    data: [{ id: 10, name: "ADMIN", permissions: [] }],
    isPending: false,
  } as never);
  vi.mocked(useBindGroup).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useUnbindGroup).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  mockUseHeldPermissions.mockReturnValue(new Set(["role:write"]));
  givenGroups(false);
});

describe("GroupAccessPage - held directory groups", () => {
  it("does not flag a group the directory pushed under an unclaimed key", () => {
    render(<GroupAccessPage />);

    expect(screen.queryByText("Awaiting confirmation")).not.toBeInTheDocument();
  });

  it("flags a held group and says its bindings grant nothing yet", () => {
    givenGroups(true);

    render(<GroupAccessPage />);

    expect(screen.getByText("Awaiting confirmation")).toBeInTheDocument();
    expect(
      screen.getByText(/confers nothing until an administrator confirms/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/bindings below take effect only once it is confirmed/i),
    ).toBeInTheDocument();
  });

  it("confirms the held group with the id of the binding waiting on it", async () => {
    const mutate = vi.fn();
    mockUseConfirmGroup.mockReturnValue({ mutate, isPending: false } as never);
    givenGroups(true);
    render(<GroupAccessPage />);

    await user.click(screen.getByRole("button", { name: /confirm group/i }));

    expect(mutate.mock.calls[0]?.[0]).toBe(1);
  });

  it("surfaces the reason when a confirmation is refused by the ceiling guard", async () => {
    const mutate = vi.fn(
      (_id: number, opts?: { onError?: (err: unknown) => void }) => {
        opts?.onError?.(
          new ApiError(
            403,
            "A role can only be assigned if every permission it grants is already held; missing: organization:delete",
          ),
        );
      },
    );
    mockUseConfirmGroup.mockReturnValue({ mutate, isPending: false } as never);
    givenGroups(true);
    render(<GroupAccessPage />);

    await user.click(screen.getByRole("button", { name: /confirm group/i }));

    expect(screen.getByRole("alert")).toHaveTextContent("organization:delete");
  });

  it("lists a held group whose last binding was deleted and says what to do", () => {
    // a group that drops its last binding while held would otherwise vanish from the page still
    // held, with no way to see the state or get out of it
    givenGroups(true, []);

    render(<GroupAccessPage />);

    expect(screen.getByText("Awaiting confirmation")).toBeInTheDocument();
    // no claim about WHY there is no binding: the waiting one may have been unbound, or a later
    // provider switch may have moved the group onto an unclaimed key while it stayed held
    expect(
      screen.getByText(/No role is bound to its current key/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Bind a role to this group to confirm it/i),
    ).toBeInTheDocument();
    // and the bindings section states the fact rather than promising access a binding cannot grant
    expect(
      screen.getByText(/No role is bound to this group yet/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /confirm group/i }),
    ).not.toBeInTheDocument();
  });

  it("shows a held group's notice but no Confirm button without role:assign", () => {
    mockUsePermissions.mockReturnValue(
      permissionState((p: string) => p === "role:read"),
    );
    givenGroups(true);

    render(<GroupAccessPage />);

    expect(screen.getByText("Awaiting confirmation")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /confirm group/i }),
    ).not.toBeInTheDocument();
  });

  it("does not offer to release a held group when any waiting role reaches past the caller", () => {
    // two bindings on the key: Auditor (in reach) and Owner (not). The address the
    // mutate call would use is bindings[0] (Auditor, in reach), so this fails against
    // an implementation that governs release by only the addressing binding.
    vi.mocked(useRoles).mockReturnValue({
      data: [
        { id: 10, name: "Auditor", permissions: ["member:read"] },
        { id: 20, name: "Owner", permissions: ["organization:export"] },
      ],
      isPending: false,
    } as never);
    mockUseHeldPermissions.mockReturnValue(new Set(["member:read"]));
    givenGroups(true, [
      { ...binding, id: 1, roleId: 10 },
      { ...binding, id: 2, roleId: 20 },
    ]);

    render(<GroupAccessPage />);
    const confirmButton = screen.getByRole("button", {
      name: "Confirm group",
    });
    const notice = confirmButton.closest("section")!;

    expect(confirmButton).toBeDisabled();
    expect(
      within(notice).getByText("Reaches beyond what this account holds."),
    ).toBeInTheDocument();
  });

  it("disables release for a waiting role the catalog cannot resolve, without claiming the ceiling is why", () => {
    givenGroups(true, [{ ...binding, id: 1, roleId: 999 }]);

    render(<GroupAccessPage />);
    const confirmButton = screen.getByRole("button", {
      name: "Confirm group",
    });
    const notice = confirmButton.closest("section")!;

    expect(confirmButton).toBeDisabled();
    expect(
      within(notice).queryByText("Reaches beyond what this account holds."),
    ).not.toBeInTheDocument();
  });

  it("offers the release when every waiting role is within reach", () => {
    vi.mocked(useRoles).mockReturnValue({
      data: [
        { id: 10, name: "Auditor", permissions: ["member:read"] },
        { id: 20, name: "Owner", permissions: ["organization:export"] },
      ],
      isPending: false,
    } as never);
    mockUseHeldPermissions.mockReturnValue(
      new Set(["member:read", "organization:export"]),
    );
    givenGroups(true, [
      { ...binding, id: 1, roleId: 10 },
      { ...binding, id: 2, roleId: 20 },
    ]);

    render(<GroupAccessPage />);

    expect(screen.getByRole("button", { name: "Confirm group" })).toBeEnabled();
  });
});
