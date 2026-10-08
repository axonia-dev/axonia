import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useDeleteRole } from "@/features/admin/api/use-role-mutations";
import { useRoleAssignees } from "@/features/admin/api/use-roles";
import type { Role } from "@/features/admin/domain/admin-user";
import { plural } from "@/lib/plural";

interface DeleteRoleDialogProps {
  role: Role | null;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

export function DeleteRoleDialog({
  role,
  isOpen,
  onOpenChange,
}: Readonly<DeleteRoleDialogProps>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title={role !== null ? `Delete “${role.name}”?` : "Delete role"}
      role="alertdialog"
    >
      {({ close }) =>
        role !== null ? <DeleteRoleForm role={role} onDone={close} /> : null
      }
    </Dialog>
  );
}

function DeleteRoleForm({
  role,
  onDone,
}: Readonly<{ role: Role; onDone: () => void }>) {
  const deleteRole = useDeleteRole();
  const assignees = useRoleAssignees(role.id);
  const assigneeCount = assignees.data?.length ?? 0;
  const guardLoaded = assignees.data !== undefined;
  const blocked = !guardLoaded || assigneeCount > 0;
  const [error, setError] = useState<string | null>(null);

  function remove() {
    setError(null);
    deleteRole.mutate(role.id, {
      onSuccess: onDone,
      onError: (e) =>
        setError(e instanceof Error ? e.message : "Could not delete the role."),
    });
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">
        This permanently removes the role. This can’t be undone.
      </p>
      {guardLoaded && assigneeCount > 0 && (
        <p className="text-muted-foreground text-sm">
          Reassign its {plural(assigneeCount, "assignee")} before deleting.
        </p>
      )}
      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}
      <DialogFooter>
        <Button variant="ghost" onPress={onDone}>
          Cancel
        </Button>
        <Button
          variant="danger"
          onPress={remove}
          isDisabled={blocked || deleteRole.isPending}
        >
          Delete
        </Button>
      </DialogFooter>
    </div>
  );
}
