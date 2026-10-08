import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { TextArea } from "@/components/ui/TextArea";
import { TextInput } from "@/components/ui/TextInput";
import {
  useCreateRole,
  useUpdateRole,
} from "@/features/admin/api/use-role-mutations";
import type { Role } from "@/features/admin/domain/admin-user";

interface RoleEditorDialogProps {
  /** The role being renamed; null when creating a new role. */
  editRole: Role | null;
  mode: "edit" | "create";
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

export function RoleEditorDialog({
  editRole,
  mode,
  isOpen,
  onOpenChange,
}: Readonly<RoleEditorDialogProps>) {
  const title = mode === "create" ? "New role" : (editRole?.name ?? "Role");
  return (
    <Dialog isOpen={isOpen} onOpenChange={onOpenChange} title={title}>
      {({ close }) => (
        <RoleEditor editRole={editRole} mode={mode} onDone={close} />
      )}
    </Dialog>
  );
}

function descriptionOrNull(value: string): string | null {
  const t = value.trim();
  return t === "" ? null : t;
}

interface RoleEditorProps {
  editRole: Role | null;
  mode: "edit" | "create";
  onDone: () => void;
}

function RoleEditor({ editRole, mode, onDone }: Readonly<RoleEditorProps>) {
  const [name, setName] = useState(() => editRole?.name ?? "");
  const [description, setDescription] = useState(
    () => editRole?.description ?? "",
  );
  const [error, setError] = useState<string | null>(null);

  const createRole = useCreateRole();
  const updateRole = useUpdateRole();
  const isSavePending = createRole.isPending || updateRole.isPending;

  function save() {
    setError(null);
    const onError = (e: unknown) =>
      setError(e instanceof Error ? e.message : "Could not save the role.");
    if (mode === "create") {
      // Permissions start empty and are granted from the matrix afterwards.
      createRole.mutate(
        {
          name: name.trim(),
          description: descriptionOrNull(description),
          permissions: [],
        },
        { onSuccess: onDone, onError },
      );
    } else if (editRole !== null) {
      // Preserve the role's existing permissions; the matrix owns those.
      updateRole.mutate(
        {
          id: editRole.id,
          body: {
            name: name.trim(),
            description: descriptionOrNull(description),
            permissions: editRole.permissions,
          },
        },
        { onSuccess: onDone, onError },
      );
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <TextInput label="Name" value={name} onChange={setName} isRequired />
      <TextArea
        label="Description"
        value={description}
        onChange={setDescription}
        rows={2}
      />
      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}
      <DialogFooter>
        <Button variant="ghost" onPress={onDone}>
          Cancel
        </Button>
        <Button onPress={save} isDisabled={name.trim() === "" || isSavePending}>
          Save
        </Button>
      </DialogFooter>
    </div>
  );
}
