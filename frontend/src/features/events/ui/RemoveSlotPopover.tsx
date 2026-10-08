import { Button } from "@/components/ui/Button";
import { DialogFooter } from "@/components/ui/DialogFooter";

interface Props {
  slotLabel: string;
  isOpen: boolean;
  isPending: boolean;
  onConfirm: () => void;
  onOpenChange: (open: boolean) => void;
}

/**
 * Confirmation popover for removing a slot from the schedule. Mirrors the
 * backlog's DeleteTopicPopover: the destructive action only fires after an
 * explicit confirm, never on the first click of the row's trash button.
 */
export function RemoveSlotPopover({
  slotLabel,
  isOpen,
  isPending,
  onConfirm,
  onOpenChange,
}: Readonly<Props>) {
  if (!isOpen) return null;

  function handleRemove() {
    onConfirm();
    onOpenChange(false);
  }

  return (
    <div
      role="dialog"
      aria-label="Confirm remove slot"
      className="border-border bg-surface absolute top-10 right-2 z-20 w-56 rounded-[var(--radius-md)] border p-3 shadow-[var(--shadow-lg)]"
    >
      <p className="text-foreground mb-3 text-sm">
        Remove <strong>{slotLabel}</strong> from the schedule?
      </p>
      <DialogFooter>
        <Button
          variant="secondary"
          size="sm"
          onPress={() => onOpenChange(false)}
        >
          Cancel
        </Button>
        <Button
          variant="danger"
          size="sm"
          isDisabled={isPending}
          onPress={handleRemove}
        >
          Remove
        </Button>
      </DialogFooter>
    </div>
  );
}
