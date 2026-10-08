import { Eye, EyeOff } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useSetOptOut } from "@/features/expertise/api/use-expertise-prefs";

interface Props {
  optedOut: boolean;
  hiddenCount: number;
}

function triggerLabel(optedOut: boolean, hiddenCount: number): string {
  if (optedOut) return "Hidden from members";
  if (hiddenCount > 0) return `Visible · ${hiddenCount} hidden`;
  return "Visible to members";
}

export function VisibilityMenu({ optedOut, hiddenCount }: Readonly<Props>) {
  const setOptOut = useSetOptOut();
  const [open, setOpen] = useState(false);
  const label = triggerLabel(optedOut, hiddenCount);

  function optOut(close: () => void) {
    setOptOut.mutate(true);
    close();
  }

  function optBackIn(close: () => void) {
    setOptOut.mutate(false);
    close();
  }

  return (
    <>
      <Button
        variant="outline"
        size="sm"
        onPress={() => setOpen(true)}
        className="gap-2"
      >
        {optedOut ? (
          <EyeOff size={15} aria-hidden />
        ) : (
          <Eye size={15} aria-hidden />
        )}
        {label}
      </Button>

      <Dialog
        isOpen={open}
        onOpenChange={setOpen}
        title="Expertise visibility"
        role="alertdialog"
      >
        {({ close }) => (
          <>
            <p className="text-foreground text-sm">
              {optedOut
                ? "Currently opted out: these expertise areas are hidden from other members. Opting back in restores discoverability."
                : "These expertise areas are visible to other members so they can find who knows what. Opting out entirely is available at any time."}
            </p>
            {hiddenCount > 0 && (
              <p className="text-muted-foreground mt-2 text-sm">
                {hiddenCount} {hiddenCount === 1 ? "area is" : "areas are"}{" "}
                hidden individually. The eye icon on an area changes that.
              </p>
            )}
            <DialogFooter>
              <Button variant="outline" onPress={close}>
                Cancel
              </Button>
              {optedOut ? (
                <Button variant="primary" onPress={() => optBackIn(close)}>
                  Opt back in
                </Button>
              ) : (
                <Button variant="danger" onPress={() => optOut(close)}>
                  Opt out
                </Button>
              )}
            </DialogFooter>
          </>
        )}
      </Dialog>
    </>
  );
}
