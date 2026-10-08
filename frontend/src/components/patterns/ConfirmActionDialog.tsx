import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";

interface ConfirmActionDialogProps {
  isOpen: boolean;
  /** Dialog heading, phrased as the question being asked. */
  title: string;
  /** What the act does, stated before the button that does it. */
  body: string;
  /** A refusal from the last attempt, or null. */
  error: string | null;
  /** The confirm button's label: the act's own verb, kept short. Usually one word, but the
   *  product's own phrase for the act wins where it is two - "Sign out", not "Leave". */
  confirmLabel: string;
  /** Disables both buttons: the act is in flight, or the page is on its way out. */
  isPending: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

/**
 * The confirmation behind a destructive act that takes nothing else down with it: one statement,
 * one refusal, Cancel and a single verb.
 *
 * Distinct from {@link ConfirmDeleteDialog}, which exists for a record whose dependents have to
 * be reassigned before it can go: that dialog carries a picker, a `needsReassign` rule and a
 * confirm button gated on the choice. Folding these two together would mean a slot and an extra
 * disabled rule that neither of this one's callers would ever pass.
 */
export function ConfirmActionDialog({
  isOpen,
  title,
  body,
  error,
  confirmLabel,
  isPending,
  onCancel,
  onConfirm,
}: Readonly<ConfirmActionDialogProps>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={(open) => {
        if (!open) onCancel();
      }}
      title={title}
      role="alertdialog"
    >
      <div className="flex flex-col gap-4">
        <p className="text-muted-foreground text-sm">{body}</p>
        {error !== null && (
          <p role="alert" className="text-danger text-sm">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button variant="ghost" onPress={onCancel} isDisabled={isPending}>
            Cancel
          </Button>
          <Button variant="danger" onPress={onConfirm} isDisabled={isPending}>
            {confirmLabel}
          </Button>
        </DialogFooter>
      </div>
    </Dialog>
  );
}
