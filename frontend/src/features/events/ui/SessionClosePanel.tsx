import { useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useCloseSession } from "@/features/events/api/use-session";
import type { Session } from "@/features/events/domain/session";
import {
  isRunning,
  needsCloseAnswer,
  nextSessionMark,
} from "@/features/events/domain/session";
import { eventsErrorMessage } from "@/features/events/events-errors";
import { useWakeAt } from "@/lib/use-wake-at";

interface Props {
  slug: string;
  session: Session;
  canManage: boolean;
}

interface CancelConfirmDialogProps {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
  isPending: boolean;
  error: unknown;
}

function CancelConfirmDialog({
  isOpen,
  onOpenChange,
  onConfirm,
  isPending,
  error,
}: Readonly<CancelConfirmDialogProps>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="This session did not happen?"
      role="alertdialog"
    >
      <div className="flex flex-col gap-4">
        <p className="text-muted-foreground text-sm">
          The session will be cancelled and its topics will return to the
          backlog. This cannot be undone.
        </p>
        <FormError error={error} message={eventsErrorMessage(error)} />
        <DialogFooter>
          <Button variant="ghost" onPress={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button variant="danger" isDisabled={isPending} onPress={onConfirm}>
            Confirm
          </Button>
        </DialogFooter>
      </div>
    </Dialog>
  );
}

function AskPanel({
  slug,
  session,
}: Readonly<{ slug: string; session: Session }>) {
  const close = useCloseSession(slug);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const answer = (held: boolean) =>
    close.mutate(
      { sessionId: session.id, input: { held } },
      { onSuccess: () => setConfirmOpen(false) },
    );

  return (
    <div className="flex flex-col gap-2">
      <div className="border-border-strong bg-surface-sunken flex items-center justify-between gap-3 rounded border border-dashed p-3">
        <p className="text-foreground text-sm font-medium">
          Did this session happen?
        </p>
        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="sm"
            isDisabled={close.isPending}
            onPress={() => {
              close.reset();
              setConfirmOpen(true);
            }}
          >
            It did not
          </Button>
          <Button
            size="sm"
            isDisabled={close.isPending}
            onPress={() => answer(true)}
          >
            It happened
          </Button>
        </div>
      </div>
      {/* Shown in exactly one place at a time: behind the dialog while it's open (where
       * CancelConfirmDialog's own FormError takes over), here otherwise - so a failed
       * confirm never produces two role="alert" nodes for the same message. */}
      {!confirmOpen && (
        <FormError
          error={close.error}
          message={eventsErrorMessage(close.error)}
        />
      )}
      <CancelConfirmDialog
        isOpen={confirmOpen}
        onOpenChange={setConfirmOpen}
        onConfirm={() => answer(false)}
        isPending={close.isPending}
        error={close.error}
      />
    </div>
  );
}

/**
 * The session page asking the one question a mailed wrap-up depends on: "Did this happen?".
 *
 * Nothing is owed while the session is still inside its own window, so the question waits for it
 * to close: asking whether something happened while it is happening reads as a defect, and the
 * header chip is already saying "Running now" beside it. A session whose length nobody recorded
 * has no knowable end, so it is never read as running and asks from its start hour as before.
 *
 * It answers to one right, matching the endpoint behind it: closing a session out is an
 * organizer's. It used to carry the held session's wrap-up too, under the community's separate
 * communication axis, and that half is gone - a release is now stated, answered and previewed in
 * one place, the facts strip's own chip, so a panel repeating it here would put the same fact on
 * the page twice with only one of the two able to act on it.
 */
export function SessionClosePanel({
  slug,
  session,
  canManage,
}: Readonly<Props>) {
  useWakeAt(nextSessionMark(session));
  if (needsCloseAnswer(session) && !isRunning(session) && canManage) {
    return <AskPanel slug={slug} session={session} />;
  }
  return null;
}
