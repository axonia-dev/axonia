import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useRemoveChatDestination } from "@/features/community/api/use-chat-destinations";
import { communityErrorMessage } from "@/features/community/community-errors";
import type { ChatDestination } from "@/features/community/domain/chat-destination";

interface RemoveDestinationConfirmProps {
  slug: string;
  destination: ChatDestination;
  close: () => void;
}

/**
 * Its own mutation, remounted per open through the `key` on its call site
 * below - the same shape `DestinationForm` uses `resetKey` for, and for the
 * same reason: `RemoveDestinationDialog` itself never unmounts, so a mutation
 * held there would keep a failed attempt's error on screen for whichever
 * destination is opened next, one nothing was ever attempted on.
 */
function RemoveDestinationConfirm({
  slug,
  destination,
  close,
}: Readonly<RemoveDestinationConfirmProps>) {
  const remove = useRemoveChatDestination(slug);

  function confirm() {
    remove.mutate(destination.id, { onSuccess: close });
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">
        axonia stops posting there. Nothing already posted is removed.
      </p>
      {remove.isError && (
        <FormError
          error={remove.error}
          message={communityErrorMessage(remove.error)}
        />
      )}
      <DialogFooter>
        <Button variant="ghost" onPress={close}>
          Cancel
        </Button>
        <Button
          variant="danger"
          onPress={confirm}
          isDisabled={remove.isPending}
        >
          Remove
        </Button>
      </DialogFooter>
    </div>
  );
}

interface RemoveDestinationDialogProps {
  slug: string;
  destination: ChatDestination | null;
  /**
   * Separate from `destination`: the caller keeps the last-targeted
   * destination in place while this goes false, so the heading stays
   * "Remove #guild?" through the exit animation rather than collapsing to
   * the no-target fallback "Remove" while `ModalOverlay` is still fading it
   * out.
   */
  isOpen: boolean;
  /** Bumped by the caller on every explicit open, the same shape
   *  `DestinationDialog`'s `resetKey` uses and for the same reason: a failed
   *  attempt at one destination must not still be on screen when the dialog
   *  reopens, even for that same destination. */
  resetKey: number;
  onOpenChange: (open: boolean) => void;
}

export function RemoveDestinationDialog({
  slug,
  destination,
  isOpen,
  resetKey,
  onOpenChange,
}: Readonly<RemoveDestinationDialogProps>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title={destination !== null ? `Remove ${destination.name}?` : "Remove"}
      role="alertdialog"
    >
      {({ close }) =>
        destination !== null && (
          <RemoveDestinationConfirm
            key={`${destination.id}-${resetKey}`}
            slug={slug}
            destination={destination}
            close={close}
          />
        )
      }
    </Dialog>
  );
}
