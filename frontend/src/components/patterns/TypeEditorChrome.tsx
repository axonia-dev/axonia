import type { ReactNode } from "react";

import { ConfirmDeleteDialog } from "@/components/patterns/ConfirmDeleteDialog";
import type { ReassignableDelete } from "@/components/patterns/useReassignableDelete";
import { Button } from "@/components/ui/Button";

interface ReassignOption {
  id: number;
  label: string;
}

interface Props {
  /** The context-specific form fields, rendered above the action bar. */
  children: ReactNode;
  onSave: () => void;
  onRevert: () => void;
  isSaving: boolean;
  /** Whether Save is enabled (e.g. a non-empty label and not mid-save). */
  canSave: boolean;
  /** A formatted save-failure message, or null. */
  saveError: string | null;
  /** Whether the record can be deleted (false for an unsaved draft). */
  canDelete: boolean;
  isDeleting: boolean;
  del: ReassignableDelete;
  deleteTitle: string;
  reassignLabel: string;
  reassignOptions: ReadonlyArray<ReassignOption>;
}

/**
 * The shared chrome for a tenant-type editor: a Save/Revert/Delete action bar, the
 * save-error alert, and the reassigning delete dialog. Context-specific fields are passed
 * as children; the delete flow is driven by useReassignableDelete.
 */
export function TypeEditorChrome({
  children,
  onSave,
  onRevert,
  isSaving,
  canSave,
  saveError,
  canDelete,
  isDeleting,
  del,
  deleteTitle,
  reassignLabel,
  reassignOptions,
}: Readonly<Props>) {
  return (
    <div className="flex flex-col gap-5">
      {children}

      <div className="border-border flex flex-wrap items-center gap-3 border-t pt-4">
        <Button onPress={onSave} isDisabled={!canSave}>
          Save
        </Button>
        <Button variant="secondary" onPress={onRevert} isDisabled={isSaving}>
          Revert
        </Button>

        {canDelete && !del.confirming && (
          <Button
            variant="ghost"
            className="ml-auto"
            onPress={del.startConfirm}
          >
            Delete
          </Button>
        )}
      </div>

      {saveError !== null && (
        <p role="alert" className="text-danger text-sm">
          {saveError}
        </p>
      )}

      <ConfirmDeleteDialog
        isOpen={del.confirming}
        title={deleteTitle}
        reassignLabel={reassignLabel}
        options={reassignOptions}
        needsReassign={del.needsReassign}
        reassignTo={del.reassignTo}
        onReassign={del.setReassignTo}
        error={del.deleteError}
        isDeleting={isDeleting}
        onCancel={del.cancel}
        onConfirm={del.confirm}
      />
    </div>
  );
}
