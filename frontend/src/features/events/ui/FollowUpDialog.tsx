import { type SyntheticEvent, useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { TextArea } from "@/components/ui/TextArea";
import { TextInput } from "@/components/ui/TextInput";
import { useActionItems } from "@/features/events/api/use-action-items";
import { useRecordFollowUp } from "@/features/events/api/use-session";
import type {
  ActionItem,
  CheckpointOutcome,
} from "@/features/events/domain/action-item";
import type { ActionItemListParams } from "@/features/events/domain/events-gateway";
import { CheckpointOutcomeList } from "@/features/events/ui/CheckpointOutcomeList";

interface Fields {
  slug: string;
  sessionId: number;
  recordingUrl: string | null;
  notes: string | null;
}

/** What this session owes an answer on: open only - an item already settled is not pinned here
 *  any more, and answering for it again is refused as NOT_PINNED_HERE, which rolls back the
 *  recording and the note with it. Sized to cover a real session rather than the server's
 *  default cap of 20, since an item left off the page is carried by omission. */
function openItemsParams(sessionId: number): ActionItemListParams {
  return {
    checkpointSessionId: sessionId,
    outcome: "OPEN",
    page: 0,
    size: 50,
  };
}

interface Props extends Fields {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Where an organizer puts the recording and their own note about a held session.
 *
 * Controlled rather than carrying its own trigger: it is opened from the facts strip when a
 * session has no recording yet, and from the session's actions menu once it has one, and a
 * component that owned its button could not be reached from both.
 */
export function FollowUpDialog({
  slug,
  sessionId,
  recordingUrl,
  notes,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="After the session"
    >
      {({ close }) => (
        <FollowUpForm
          slug={slug}
          sessionId={sessionId}
          recordingUrl={recordingUrl}
          notes={notes}
          onDone={close}
        />
      )}
    </Dialog>
  );
}

interface FormProps extends Readonly<Fields> {
  onDone: () => void;
}

interface OpenPointsProps {
  isPending: boolean;
  isError: boolean;
  items: readonly ActionItem[];
  total: number;
  value: Record<number, CheckpointOutcome>;
  onChange: (value: Record<number, CheckpointOutcome>) => void;
}

/**
 * The list of open points, and what stands in for it when there is none to show.
 *
 * This dialog is the one that writes, so a list that never arrived is not the same thing as a
 * session owing nothing: both render no rows, and saving through the first leaves every open
 * item pinned to this session and still OPEN, with nothing on screen saying the ritual did not
 * run. A failed load says so and Save is held. A load still in flight says so too, since a held
 * Save with no reason given reads as a broken dialog.
 */
function OpenPoints({
  isPending,
  isError,
  items,
  total,
  value,
  onChange,
}: Readonly<OpenPointsProps>) {
  if (isError) {
    return (
      <p role="alert" className="text-danger text-sm">
        Could not load what this session owes. Saving is held until it loads.
      </p>
    );
  }
  if (isPending) {
    return (
      <p className="text-muted-foreground text-sm">
        Loading what this session owes.
      </p>
    );
  }
  return (
    <CheckpointOutcomeList
      items={items}
      total={total}
      value={value}
      onChange={onChange}
    />
  );
}

function FollowUpForm({
  slug,
  sessionId,
  recordingUrl,
  notes,
  onDone,
}: Readonly<FormProps>) {
  const [url, setUrl] = useState(recordingUrl ?? "");
  const [note, setNote] = useState(notes ?? "");
  const [outcomes, setOutcomes] = useState<Record<number, CheckpointOutcome>>(
    {},
  );
  const record = useRecordFollowUp(slug, sessionId);
  const {
    data,
    isPending: itemsPending,
    isError: itemsFailed,
  } = useActionItems(slug, openItemsParams(sessionId));
  const openItems = data?.content ?? [];
  const openItemCount = data?.totalElements ?? openItems.length;
  const decisionsUnknown = itemsPending || itemsFailed;

  function handleSubmit(event: SyntheticEvent) {
    event.preventDefault();
    if (decisionsUnknown) {
      return;
    }
    const decisions = openItems.map((item) => ({
      actionItemId: item.id,
      outcome: outcomes[item.id] ?? ("CARRIED" as const),
    }));
    record.mutate(
      { recordingUrl: url.trim(), notes: note.trim(), decisions },
      { onSuccess: onDone },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <TextInput
        label="Recording link"
        value={url}
        onChange={setUrl}
        placeholder="https://"
      />
      <TextArea
        label="Note"
        value={note}
        onChange={setNote}
        rows={4}
        placeholder="What came out of the session."
      />
      <OpenPoints
        isPending={itemsPending}
        isError={itemsFailed}
        items={openItems}
        total={openItemCount}
        value={outcomes}
        onChange={setOutcomes}
      />
      <FormError error={record.error} />
      <DialogFooter>
        <Button variant="ghost" onPress={onDone}>
          Cancel
        </Button>
        <Button type="submit" isDisabled={record.isPending || decisionsUnknown}>
          Save
        </Button>
      </DialogFooter>
    </form>
  );
}
