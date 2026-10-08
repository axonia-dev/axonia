import { ConfirmActionDialog } from "@/components/patterns/ConfirmActionDialog";

import type { AgentConnection } from "../domain/agent-connection";

interface DisconnectDialogProps {
  connection: AgentConnection | null;
  isDisconnecting: boolean;
  isError: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function DisconnectDialog({
  connection,
  isDisconnecting,
  isError,
  onCancel,
  onConfirm,
}: Readonly<DisconnectDialogProps>) {
  return (
    <ConfirmActionDialog
      isOpen={connection !== null}
      title={
        connection === null
          ? "Disconnect this app?"
          : `Disconnect ${connection.clientName}?`
      }
      body="The app loses access immediately. Connecting again asks for consent."
      error={isError ? "Could not disconnect. Try again." : null}
      confirmLabel="Disconnect"
      isPending={isDisconnecting}
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
}
