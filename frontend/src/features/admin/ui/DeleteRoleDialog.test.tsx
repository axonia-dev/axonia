import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useDeleteRole } from "@/features/admin/api/use-role-mutations";
import { useRoleAssignees } from "@/features/admin/api/use-roles";
import type { Role } from "@/features/admin/domain/admin-user";
import { DeleteRoleDialog } from "@/features/admin/ui/DeleteRoleDialog";

vi.mock("@/features/admin/api/use-roles", () => ({
  useRoleAssignees: vi.fn(),
}));

vi.mock("@/features/admin/api/use-role-mutations", () => ({
  useDeleteRole: vi.fn(),
}));

const role: Role = {
  id: 2,
  name: "Curator",
  description: null,
  system: false,
  permissions: ["library_type:read"],
};

type DeleteFn = ReturnType<typeof useDeleteRole>["mutate"];

function mutation(mutate: unknown = vi.fn()) {
  return { mutate, isPending: false } as never;
}

function assignees(data: unknown[] | undefined, isPending = false) {
  return { data, isPending } as never;
}

describe("DeleteRoleDialog", () => {
  beforeEach(() => {
    vi.mocked(useRoleAssignees).mockReturnValue(assignees([]));
    vi.mocked(useDeleteRole).mockReturnValue(mutation());
  });

  it("deletes the role on confirm", async () => {
    const deleteMutate = vi.fn();
    vi.mocked(useDeleteRole).mockReturnValue(mutation(deleteMutate));

    const user = userEvent.setup({ delay: null });
    render(<DeleteRoleDialog role={role} isOpen onOpenChange={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: /^Delete$/i }));
    expect(deleteMutate).toHaveBeenCalledWith(2, expect.anything());
  });

  it("blocks deletion while the role has assignees", () => {
    vi.mocked(useRoleAssignees).mockReturnValue(assignees([{ id: 1 }]));
    render(<DeleteRoleDialog role={role} isOpen onOpenChange={vi.fn()} />);

    expect(screen.getByRole("button", { name: /^Delete$/i })).toBeDisabled();
    expect(screen.getByText(/Reassign its 1 assignee/)).toBeInTheDocument();
  });

  it("keeps Delete disabled until the assignee guard has loaded", () => {
    vi.mocked(useRoleAssignees).mockReturnValue(assignees(undefined, true));
    render(<DeleteRoleDialog role={role} isOpen onOpenChange={vi.fn()} />);

    expect(screen.getByRole("button", { name: /^Delete$/i })).toBeDisabled();
    // An unresolved guard knows no count, so the reassign line stays away rather than
    // claiming "Reassign its 0 assignees" from a default that has answered nothing.
    expect(screen.queryByText(/Reassign its/)).not.toBeInTheDocument();
  });

  it("pluralises the assignee count for a single assignee", () => {
    vi.mocked(useRoleAssignees).mockReturnValue(assignees([{ id: 1 }]));
    render(<DeleteRoleDialog role={role} isOpen onOpenChange={vi.fn()} />);

    expect(
      screen.getByText("Reassign its 1 assignee before deleting."),
    ).toBeInTheDocument();
  });

  it("pluralises the assignee count for multiple assignees", () => {
    vi.mocked(useRoleAssignees).mockReturnValue(
      assignees([{ id: 1 }, { id: 2 }]),
    );
    render(<DeleteRoleDialog role={role} isOpen onOpenChange={vi.fn()} />);

    expect(
      screen.getByText("Reassign its 2 assignees before deleting."),
    ).toBeInTheDocument();
  });

  it("shows an error alert when deletion fails", async () => {
    const deleteMutate = vi.fn<DeleteFn>((_id, opts) => {
      opts?.onError?.(
        new Error("delete failed"),
        _id,
        undefined,
        undefined as never,
      );
    });
    vi.mocked(useDeleteRole).mockReturnValue(mutation(deleteMutate));

    const user = userEvent.setup({ delay: null });
    render(<DeleteRoleDialog role={role} isOpen onOpenChange={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: /^Delete$/i }));
    expect(screen.getByRole("alert")).toHaveTextContent("delete failed");
  });
});
