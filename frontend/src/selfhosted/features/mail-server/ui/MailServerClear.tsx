import { useState } from "react";

import { ConfirmActionDialog } from "@/components/patterns/ConfirmActionDialog";
import { Button } from "@/components/ui/Button";
import { useClearMailServer } from "@/selfhosted/features/mail-server/api/use-mail-server";
import { STALE_CLEAR } from "@/selfhosted/features/mail-server/domain/mail-server-copy";
import { isStale } from "@/selfhosted/features/mail-server/ui/use-failures";
import { refusedCallMessage } from "@/selfhosted/features/mail-server/ui/use-mail-server-form";

type Props = Readonly<{
  baseRevision: number;
  onStale: () => void;
  onCleared: () => void;
}>;

/** Clears what was stored at the revision the form opened at. A refusal is said in the dialog
 *  until it closes; a stale one also marks the form behind it stale. */
export function MailServerClear({ baseRevision, onStale, onCleared }: Props) {
  const [confirming, setConfirming] = useState(false);
  const [refusal, setRefusal] = useState<string | null>(null);
  const clear = useClearMailServer();

  function confirm(open: boolean) {
    setRefusal(null);
    setConfirming(open);
  }

  function refused(error: Error) {
    if (isStale(error)) onStale();
    setRefusal(
      isStale(error)
        ? STALE_CLEAR
        : (refusedCallMessage(error) ?? "Could not clear the mail server."),
    );
  }

  return (
    <>
      <Button variant="outline" onPress={() => confirm(true)}>
        Clear
      </Button>
      <ConfirmActionDialog
        isOpen={confirming}
        title="Clear the mail server?"
        body="Mail stops until it is set up again. Fields set by the deployment stay."
        error={refusal}
        confirmLabel="Clear"
        isPending={clear.isPending}
        onCancel={() => confirm(false)}
        onConfirm={() =>
          clear.mutate(baseRevision, { onSuccess: onCleared, onError: refused })
        }
      />
    </>
  );
}
