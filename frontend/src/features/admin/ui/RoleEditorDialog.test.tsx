import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCreateRole,
  useUpdateRole,
} from "@/features/admin/api/use-role-mutations";
import type { Role } from "@/features/admin/domain/admin-user";
import { RoleEditorDialog } from "@/features/admin/ui/RoleEditorDialog";

vi.mock("@/features/admin/api/use-role-mutations", () => ({
  useCreateRole: vi.fn(),
  useUpdateRole: vi.fn(),
}));

const custom: Role = {
  id: 2,
  name: "Curator",
  description: "Looks after the library",
  system: false,
  permissions: ["library_type:read"],
};

type UpdateFn = ReturnType<typeof useUpdateRole>["mutate"];

/** Build a minimal partial mutation-hook return typed as `never` so the cast is safe. */
function mutation(mutate: unknown = vi.fn()) {
  return { mutate, isPending: false } as never;
}

describe("RoleEditorDialog", () => {
  beforeEach(() => {
    vi.mocked(useCreateRole).mockReturnValue(mutation());
    vi.mocked(useUpdateRole).mockReturnValue(mutation());
  });

  it("shows editable name/description fields but no permissions in edit mode", () => {
    render(
      <RoleEditorDialog
        editRole={custom}
        mode="edit"
        isOpen
        onOpenChange={vi.fn()}
      />,
    );
    expect(screen.getByLabelText("Name")).toHaveValue("Curator");
    expect(screen.getByLabelText("Description")).toHaveValue(
      "Looks after the library",
    );
    expect(screen.getByRole("button", { name: /^Save$/i })).toBeInTheDocument();
    // Permissions, duplicate and delete now live on the roles page, not here.
    expect(
      screen.queryByRole("button", { name: "Organization write" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Duplicate/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Delete/ }),
    ).not.toBeInTheDocument();
  });

  it("create mode: empty Name, calls createRole.mutate with no permissions on Save", async () => {
    const createMutate = vi.fn();
    vi.mocked(useCreateRole).mockReturnValue(mutation(createMutate));

    const user = userEvent.setup({ delay: null });
    render(
      <RoleEditorDialog
        editRole={null}
        mode="create"
        isOpen
        onOpenChange={vi.fn()}
      />,
    );

    expect(screen.getByLabelText("Name")).toHaveValue("");

    await user.type(screen.getByLabelText("Name"), "My Role");
    await user.click(screen.getByRole("button", { name: /^Save$/i }));

    expect(createMutate).toHaveBeenCalledWith(
      { name: "My Role", description: null, permissions: [] },
      expect.anything(),
    );
  });

  it("edit Save preserves the role's existing permissions", async () => {
    const updateMutate = vi.fn<UpdateFn>();
    vi.mocked(useUpdateRole).mockReturnValue(mutation(updateMutate));

    const user = userEvent.setup({ delay: null });
    render(
      <RoleEditorDialog
        editRole={custom}
        mode="edit"
        isOpen
        onOpenChange={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("button", { name: /^Save$/i }));

    expect(updateMutate).toHaveBeenCalledWith(
      {
        id: 2,
        body: {
          name: "Curator",
          description: "Looks after the library",
          permissions: ["library_type:read"],
        },
      },
      expect.anything(),
    );
  });

  it("shows error alert when Save fails", async () => {
    const updateMutate = vi.fn<UpdateFn>((_vars, opts) => {
      opts?.onError?.(new Error("boom"), _vars, undefined, undefined as never);
    });
    vi.mocked(useUpdateRole).mockReturnValue(mutation(updateMutate));

    const user = userEvent.setup({ delay: null });
    render(
      <RoleEditorDialog
        editRole={custom}
        mode="edit"
        isOpen
        onOpenChange={vi.fn()}
      />,
    );
    await user.click(screen.getByRole("button", { name: /^Save$/i }));
    expect(screen.getByRole("alert")).toHaveTextContent("boom");
  });
});
