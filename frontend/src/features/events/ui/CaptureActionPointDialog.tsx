import { type SyntheticEvent, useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import { useRaiseActionItem } from "@/features/events/api/use-action-items";
import type { ActionItemFormValue } from "@/features/events/ui/ActionItemFields";
import {
  ActionItemFields,
  actionItemInputOf,
  EMPTY_ACTION_ITEM_FORM,
} from "@/features/events/ui/ActionItemFields";

interface Props {
  slug: string;
  sessionId: number;
}

/**
 * Trigger + dialog for capturing an action point tied to a held session's discussion
 * (mode `capture`). A title, an assignee and the session it is answered at are the only
 * fields shown up front - description and due date are asked for only on request, behind
 * "More".
 */
export function CaptureActionPointDialog({ slug, sessionId }: Readonly<Props>) {
  const [open, setOpen] = useState(false);

  return (
    <>
      <Button size="sm" onPress={() => setOpen(true)}>
        + Action point
      </Button>
      <Dialog
        isOpen={open}
        onOpenChange={setOpen}
        title="Capture an action point"
      >
        {({ close }) => (
          <CaptureActionPointForm
            slug={slug}
            sessionId={sessionId}
            onDone={close}
          />
        )}
      </Dialog>
    </>
  );
}

interface FormProps {
  slug: string;
  sessionId: number;
  onDone: () => void;
}

function CaptureActionPointForm({
  slug,
  sessionId,
  onDone,
}: Readonly<FormProps>) {
  const [value, setValue] = useState<ActionItemFormValue>(
    EMPTY_ACTION_ITEM_FORM,
  );

  const raise = useRaiseActionItem(slug);
  const disabled = value.title.trim() === "";

  function handleSubmit(event: SyntheticEvent) {
    event.preventDefault();
    raise.mutate(
      { ...actionItemInputOf(value), originSessionId: sessionId },
      { onSuccess: onDone },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <ActionItemFields slug={slug} value={value} onChange={setValue} />
      <FormError error={raise.isError} />
      <DialogFooter>
        <Button variant="ghost" size="sm" onPress={onDone}>
          Cancel
        </Button>
        <Button
          type="submit"
          size="sm"
          isDisabled={disabled || raise.isPending}
        >
          Capture
        </Button>
      </DialogFooter>
    </form>
  );
}
