import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { Select } from "@/components/ui/Select";

interface ReassignOption {
  id: number;
  label: string;
}

interface ConfirmDeleteDialogProps {
  isOpen: boolean;
  /** Dialog heading, e.g. "Delete this kind?". */
  title: string;
  /** Label for the reassignment picker, e.g. "Move communities to…". */
  reassignLabel: string;
  /** Targets the deleted record's dependents can be reassigned to. */
  options: ReadonlyArray<ReassignOption>;
  /** Show the reassignment picker — the record still has dependents. */
  needsReassign: boolean;
  /** Overrides the default "This cannot be undone." body copy, e.g. to name what a
   *  reassignment-free removal takes down along with the record. */
  message?: string;
  /** The confirm button's verb, when the title says something other than "Delete". One act, one word. */
  confirmLabel?: string;
  reassignTo: number | null;
  onReassign: (id: number) => void;
  /** A failed delete, surfaced in the dialog. */
  error: string | null;
  isDeleting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * A reusable destructive-confirmation dialog. When the record still has dependents
 * (`needsReassign`), the confirm button stays disabled until a reassignment target
 * is chosen, so the dependents are never orphaned.
 */
export function ConfirmDeleteDialog({
  isOpen,
  title,
  reassignLabel,
  options,
  needsReassign,
  message,
  confirmLabel = "Delete",
  reassignTo,
  onReassign,
  error,
  isDeleting,
  onCancel,
  onConfirm,
}: Readonly<ConfirmDeleteDialogProps>) {
  return (
    <Dialog
      isOpen={isOpen}
      // Escape or a dismiss request cancels and resets the delete flow.
      onOpenChange={(open) => {
        if (!open) {
          onCancel();
        }
      }}
      title={title}
      role="alertdialog"
    >
      <div className="flex flex-col gap-4">
        <p className="text-muted-foreground text-sm">
          {message ?? "This cannot be undone."}
        </p>
        {error !== null && (
          <p role="alert" className="text-danger text-sm">
            {error}
          </p>
        )}
        {needsReassign && (
          <Select
            label={reassignLabel}
            value={reassignTo}
            options={options}
            onChange={onReassign}
            placeholder={reassignLabel}
            className="max-w-xs"
          />
        )}
        <DialogFooter>
          <Button variant="ghost" onPress={onCancel} isDisabled={isDeleting}>
            Cancel
          </Button>
          <Button
            variant="danger"
            onPress={onConfirm}
            isDisabled={isDeleting || (needsReassign && reassignTo === null)}
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </div>
    </Dialog>
  );
}
