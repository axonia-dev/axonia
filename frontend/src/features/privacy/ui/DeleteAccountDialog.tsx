import { useState } from "react";

import { signOut } from "@/auth/account-state";
import { isOwnSignInRefusal } from "@/auth/own-sign-in-refusal";
import { useAuth } from "@/auth/session";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { TextInput } from "@/components/ui/TextInput";
import { useEraseAccount } from "@/features/privacy/api/use-erase-account";
import { useErasurePreflight } from "@/features/privacy/api/use-erasure-preflight";
import type {
  ErasureBlocker,
  ErasurePreflight,
} from "@/features/privacy/domain/erasure";
import { blockerMessage } from "@/features/privacy/ui/blocker-message";
import { ApiError } from "@/lib/api-client";

const ACCOUNT_ERASURE_BLOCKED = "ACCOUNT_ERASURE_BLOCKED";
const ACCOUNT_ERASURE_INCOMPLETE = "ACCOUNT_ERASURE_INCOMPLETE";

type PreflightStatus = "loading" | "error" | "permitted" | "blocked";

interface Props {
  /** The person's own email address: what they must retype to confirm. Undefined
   *  while it is still unknown (e.g. /me has not loaded yet). */
  email: string | undefined;
  isOpen: boolean;
  onOpenChange: (isOpen: boolean) => void;
}

/**
 * The self-serve member's exit path: typed-email confirmation mirroring
 * the hub deletion dialog, gated by a live preflight rather than a static
 * warning, because whether this is even possible depends on communities the
 * person still owns. The backend re-checks the same guards at erase time, so
 * a stale "permitted" answer (someone made the person a sole owner while this
 * was open) is re-fetched rather than shown as a generic error.
 */
export function DeleteAccountDialog({
  email,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="Delete this account?"
      role="alertdialog"
    >
      {({ close }) => <DeleteAccountForm email={email} onClose={close} />}
    </Dialog>
  );
}

function erasureErrorCode(error: unknown): string | undefined {
  return error instanceof ApiError ? error.code : undefined;
}

// REQUIRES_OWN_SESSION is defence in depth: the settings hide both privacy
// items from a delegated session, so this refusal should never reach the
// dialog. If it ever does, "Something went wrong" would send the person
// looking for a fault that is not there.
function isGenericError(error: unknown): boolean {
  if (error == null) return false;
  if (isOwnSignInRefusal(error)) return false;
  const code = erasureErrorCode(error);
  return (
    code !== ACCOUNT_ERASURE_BLOCKED && code !== ACCOUNT_ERASURE_INCOMPLETE
  );
}

function canDeleteNow(
  status: PreflightStatus,
  matches: boolean,
  isPending: boolean,
): boolean {
  return status === "permitted" && matches && !isPending;
}

function preflightStatus(
  data: ErasurePreflight | undefined,
  isError: boolean,
): PreflightStatus {
  if (isError) return "error";
  if (data === undefined) return "loading";
  return data.permitted ? "permitted" : "blocked";
}

function BlockerList({ blockers }: Readonly<{ blockers: ErasureBlocker[] }>) {
  return (
    <ul className="flex flex-col gap-2 text-sm">
      {blockers.map((blocker) => (
        <li key={`${blocker.kind}-${blocker.organizationId}`}>
          {blockerMessage(blocker)}
        </li>
      ))}
    </ul>
  );
}

function PreflightBody({
  status,
  blockers,
  email,
  confirmation,
  onConfirmationChange,
  onRetry,
}: Readonly<{
  status: PreflightStatus;
  blockers: ErasureBlocker[];
  email: string;
  confirmation: string;
  onConfirmationChange: (value: string) => void;
  onRetry: () => void;
}>) {
  if (status === "loading") {
    return (
      <p className="text-muted-foreground text-sm">
        Checking whether this is possible right now...
      </p>
    );
  }
  if (status === "error") {
    return (
      <div className="flex flex-col items-start gap-2">
        <p role="alert" className="text-danger text-sm">
          Could not check whether this is possible right now.
        </p>
        <Button variant="outline" size="sm" onPress={onRetry}>
          Try again
        </Button>
      </div>
    );
  }
  if (status === "blocked") {
    return <BlockerList blockers={blockers} />;
  }
  return (
    <TextInput
      label={`Type "${email}" to confirm`}
      value={confirmation}
      onChange={onConfirmationChange}
      placeholder={email}
    />
  );
}

/**
 * Which failure the person is looking at, kept out of DeleteAccountForm so that
 * form stays inside the complexity ceiling and so each refusal reads as its own
 * branch rather than a chain of flags.
 */
function ErasureFailureNotice({ error }: Readonly<{ error: unknown }>) {
  if (isOwnSignInRefusal(error)) {
    return (
      <p role="alert" className="text-danger text-sm">
        Deleting this account needs the person’s own sign-in.
      </p>
    );
  }
  if (!isGenericError(error)) return null;
  return (
    <p role="alert" className="text-danger text-sm">
      Something went wrong. Please try again.
    </p>
  );
}

function MissingEmailNotice({ onClose }: Readonly<{ onClose: () => void }>) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">
        No email address is on file for this account yet. Try again in a moment.
      </p>
      <DialogFooter>
        <Button variant="ghost" onPress={onClose}>
          Close
        </Button>
      </DialogFooter>
    </div>
  );
}

function IncompleteNotice({ onClose }: Readonly<{ onClose: () => void }>) {
  return (
    <div className="flex flex-col gap-4">
      <p role="status" className="text-sm">
        The data has been erased everywhere. The one thing left, removing the
        sign-in, is being retried. Signing out now.
      </p>
      <DialogFooter>
        <Button variant="ghost" onPress={onClose}>
          Close
        </Button>
      </DialogFooter>
    </div>
  );
}

function DeleteAccountForm({
  email,
  onClose,
}: Readonly<{ email: string | undefined; onClose: () => void }>) {
  const [confirmation, setConfirmation] = useState("");
  const [incomplete, setIncomplete] = useState(false);
  const [blockedNotice, setBlockedNotice] = useState(false);
  const auth = useAuth();
  const preflight = useErasurePreflight(email !== undefined);
  const eraseAccount = useEraseAccount();

  if (email === undefined) {
    return <MissingEmailNotice onClose={onClose} />;
  }

  const status = preflightStatus(preflight.data, preflight.isError);
  const matches = confirmation.length > 0 && confirmation === email;
  const canDelete = canDeleteNow(status, matches, eraseAccount.isPending);

  function handleEraseError(error: unknown) {
    const code = erasureErrorCode(error);
    if (code === ACCOUNT_ERASURE_BLOCKED) {
      setBlockedNotice(true);
      void preflight.refetch();
      return;
    }
    if (code === ACCOUNT_ERASURE_INCOMPLETE) {
      setIncomplete(true);
      signOut(auth);
    }
  }

  function confirmDelete() {
    setBlockedNotice(false);
    eraseAccount.mutate(undefined, {
      onSuccess: () => {
        onClose();
        signOut(auth);
      },
      onError: handleEraseError,
    });
  }

  if (incomplete) {
    return <IncompleteNotice onClose={onClose} />;
  }

  // Not shown alongside "blocked": the blocker list already explains the refusal.
  // Shown for "error" too - the refetch this same notice triggered can itself
  // fail, and without this the person sees only a check-failed message with no
  // sign their Delete press did anything at all.
  const showBlockedNotice = blockedNotice && status !== "blocked";

  return (
    <div className="flex flex-col gap-4">
      {status !== "blocked" && (
        <p className="text-muted-foreground text-sm">
          This permanently erases this account and everything tied to it, in
          every community it belongs to. This cannot be undone.
        </p>
      )}
      <PreflightBody
        status={status}
        blockers={preflight.data?.blockers ?? []}
        email={email}
        confirmation={confirmation}
        onConfirmationChange={setConfirmation}
        onRetry={() => void preflight.refetch()}
      />
      {showBlockedNotice && (
        <p role="alert" className="text-danger text-sm">
          That did not go through - something changed. Try again.
        </p>
      )}
      <ErasureFailureNotice error={eraseAccount.error} />
      <DialogFooter>
        <Button variant="ghost" onPress={onClose}>
          Cancel
        </Button>
        <Button
          variant="danger"
          isDisabled={!canDelete}
          onPress={confirmDelete}
        >
          Delete
        </Button>
      </DialogFooter>
    </div>
  );
}
