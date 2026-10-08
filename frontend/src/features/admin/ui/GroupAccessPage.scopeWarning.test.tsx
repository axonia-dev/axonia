import { render, screen } from "@testing-library/react";
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

const entraGroups = {
  source: "GRAPH",
  groups: [
    {
      groupId: "grp-eng",
      displayName: "Engineering",
      inDirectory: true,
      awaitingConfirmation: false,
      bindings: [
        {
          id: 1,
          subjectType: "GROUP",
          subjectId: "grp-eng",
          roleId: 10,
          subjectLabel: null,
        },
      ],
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(usePermissions).mockReturnValue(permissionState(() => true));
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
  vi.mocked(useConfirmGroup).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as never);
  vi.mocked(useHeldPermissions).mockReturnValue(new Set(["role:write"]));
  mockUseIdpGroups.mockReturnValue({
    data: entraGroups,
    isPending: false,
  } as never);
});

describe("GroupAccessPage - provisioning scope warning", () => {
  it("shows the provisioning-scope warning once for an Entra-backed source", () => {
    render(<GroupAccessPage />);

    expect(screen.getAllByRole("note")).toHaveLength(1);
  });

  it("omits the provisioning-scope warning for a non-Entra source", () => {
    mockUseIdpGroups.mockReturnValue({
      data: { source: "NONE", groups: [] },
      isPending: false,
    } as never);

    render(<GroupAccessPage />);

    expect(screen.queryByRole("note")).not.toBeInTheDocument();
  });

  it("places the provisioning-scope warning above the group list", () => {
    render(<GroupAccessPage />);

    const note = screen.getByRole("note");
    const groupList = screen.getByText("Engineering").closest("ul")!;
    expect(
      note.compareDocumentPosition(groupList) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("shows the provisioning-scope warning above the empty state", () => {
    mockUseIdpGroups.mockReturnValue({
      data: { source: "SCIM", groups: [] },
      isPending: false,
    } as never);

    render(<GroupAccessPage />);

    const note = screen.getByRole("note");
    const empty = screen.getByText("No bindings yet");
    expect(
      note.compareDocumentPosition(empty) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });
});
