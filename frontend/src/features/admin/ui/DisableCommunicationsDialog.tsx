import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";

interface Props {
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
  onConfirm: () => void;
}

/**
 * The one confirmation the organization kill switch needs before it flips off: it
 * is destructive enough (the widest governance layer, immediate, tenant
 * wide) to demand a deliberate choice rather than a stray click. Turning it back on
 * carries no dialog - only turning email and chat off does.
 */
export function DisableCommunicationsDialog({
  isOpen,
  onOpenChange,
  onConfirm,
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="Turn off email and chat for everyone?"
      role="alertdialog"
    >
      {({ close }) => (
        <div className="flex flex-col gap-4">
          <p className="text-muted-foreground text-sm">
            This immediately turns off email and chat notifications for everyone
            in this organization, until it is switched back on. The in-app
            notification panel is untouched: nothing sent there goes missing.
          </p>
          <DialogFooter>
            <Button variant="ghost" onPress={close}>
              Cancel
            </Button>
            <Button
              variant="danger"
              onPress={() => {
                onConfirm();
                close();
              }}
            >
              Disable
            </Button>
          </DialogFooter>
        </div>
      )}
    </Dialog>
  );
}
