import { type ReactNode, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useCommunity } from "@/features/community/api/use-communities";
import {
  useCalendarSubscription,
  useResetCalendarSubscription,
  useSubscribeToCalendar,
} from "@/features/events/api/use-calendar-subscription";
import { SubscriptionPanel } from "@/features/events/ui/CalendarSubscribePanels";

interface CalendarSubscribeDialogProps {
  slug: string;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * The schedule as a feed a calendar subscribes to once.
 *
 * Opening this dialog reads and never writes. A subscription is a credential that resolves
 * publicly and that the API offers no way to delete, so it is minted on an explicit press and
 * never as a side effect of looking. Its URL is readable only in the answer that minted it,
 * because only a hash of the token is kept, so a member returning later meets a subscription
 * that still works and can no longer be read out. That is a state of its own rather than an
 * error: it says so, carries the sync line that proves the link is live, and offers the reset
 * that is the only way back to a readable one.
 */
export function CalendarSubscribeDialog({
  slug,
  isOpen,
  onOpenChange,
}: Readonly<CalendarSubscribeDialogProps>) {
  const [confirmingReset, setConfirmingReset] = useState(false);

  function handleOpenChange(open: boolean) {
    if (!open) setConfirmingReset(false);
    onOpenChange(open);
  }

  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={handleOpenChange}
      title={confirmingReset ? "Reset the calendar link?" : "Add to calendar"}
      role={confirmingReset ? "alertdialog" : "dialog"}
    >
      {({ close }) => (
        <SubscribeBody
          slug={slug}
          confirmingReset={confirmingReset}
          onConfirmingResetChange={setConfirmingReset}
          onDone={close}
        />
      )}
    </Dialog>
  );
}

interface SubscribeBodyProps {
  slug: string;
  confirmingReset: boolean;
  onConfirmingResetChange: (confirming: boolean) => void;
  onDone: () => void;
}

function failureText(isError: boolean, message: string): string | null {
  return isError ? message : null;
}

function SubscribeBody({
  slug,
  confirmingReset,
  onConfirmingResetChange,
  onDone,
}: Readonly<SubscribeBodyProps>) {
  const subscription = useCalendarSubscription(slug);
  const community = useCommunity(slug);
  const subscribe = useSubscribeToCalendar(slug);
  const reset = useResetCalendarSubscription(slug);

  if (confirmingReset) {
    return (
      <ResetConfirmation
        isPending={reset.isPending}
        error={failureText(
          reset.isError,
          "The link could not be reset. Try again.",
        )}
        onCancel={() => onConfirmingResetChange(false)}
        onConfirm={() =>
          reset.mutate(undefined, {
            onSuccess: () => onConfirmingResetChange(false),
          })
        }
      />
    );
  }
  if (subscription.isPending) {
    return (
      <StatusPanel onDone={onDone}>
        <p className="text-muted-foreground text-sm">Preparing the link</p>
      </StatusPanel>
    );
  }
  if (subscription.isError) {
    return (
      <StatusPanel onDone={onDone} onRetry={() => void subscription.refetch()}>
        <p role="alert" className="text-danger text-sm">
          The calendar link could not be loaded.
        </p>
      </StatusPanel>
    );
  }
  if (subscription.data === null) {
    return (
      <NotSubscribed
        isPending={subscribe.isPending}
        error={failureText(
          subscribe.isError,
          "The calendar link could not be created. Try again.",
        )}
        onSubscribe={() => subscribe.mutate()}
        onDone={onDone}
      />
    );
  }
  return (
    <SubscriptionPanel
      subscription={subscription.data}
      name={community.data?.name ?? slug}
      onReset={() => onConfirmingResetChange(true)}
      onDone={onDone}
    />
  );
}

/** Loading and failure still need a way out: the panel is the whole dialog, and Dialog
 *  carries no close button of its own. */
function StatusPanel({
  children,
  onDone,
  onRetry,
}: Readonly<{
  children: ReactNode;
  onDone: () => void;
  onRetry?: () => void;
}>) {
  return (
    <div className="flex flex-col gap-4">
      {children}
      <DialogFooter>
        <Button variant="ghost" onPress={onDone}>
          Done
        </Button>
        {onRetry !== undefined && <Button onPress={onRetry}>Try again</Button>}
      </DialogFooter>
    </div>
  );
}

interface NotSubscribedProps {
  isPending: boolean;
  error: string | null;
  onSubscribe: () => void;
  onDone: () => void;
}

/** Nothing exists yet, and nothing is created until this is pressed. */
function NotSubscribed({
  isPending,
  error,
  onSubscribe,
  onDone,
}: Readonly<NotSubscribedProps>) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">
        A calendar link publishes this schedule to any calendar that subscribes
        to it. The link is shown once, at the moment it is created.
      </p>
      {error !== null && (
        <p role="alert" className="text-danger text-sm">
          {error}
        </p>
      )}
      <DialogFooter>
        <Button variant="ghost" onPress={onDone}>
          Cancel
        </Button>
        <Button onPress={onSubscribe} isDisabled={isPending}>
          Create link
        </Button>
      </DialogFooter>
    </div>
  );
}

interface ResetConfirmationProps {
  isPending: boolean;
  error: string | null;
  onCancel: () => void;
  onConfirm: () => void;
}

function ResetConfirmation({
  isPending,
  error,
  onCancel,
  onConfirm,
}: Readonly<ResetConfirmationProps>) {
  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">
        Every calendar holding the current link stops syncing, and the
        replacement has to be pasted into each one again.
      </p>
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
          Reset the link
        </Button>
      </DialogFooter>
    </div>
  );
}
