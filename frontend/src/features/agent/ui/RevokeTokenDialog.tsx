import { ConfirmActionDialog } from "@/components/patterns/ConfirmActionDialog";

import type { PersonalToken } from "../domain/personal-access-token";

function revokeDialogTitle(pendingToken: PersonalToken | null): string {
  if (pendingToken === null) {
    return "Revoke this key?";
  }
  return `Revoke "${pendingToken.name}"?`;
}

interface RevokeTokenDialogProps {
  token: PersonalToken | null;
  isRevoking: boolean;
  isError: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function RevokeTokenDialog({
  token,
  isRevoking,
  isError,
  onCancel,
  onConfirm,
}: Readonly<RevokeTokenDialogProps>) {
  return (
    <ConfirmActionDialog
      isOpen={token !== null}
      title={revokeDialogTitle(token)}
      body="The key stops working immediately. This cannot be undone."
      error={isError ? "Could not revoke the key. Try again." : null}
      confirmLabel="Revoke"
      isPending={isRevoking}
      onCancel={onCancel}
      onConfirm={onConfirm}
    />
  );
}
