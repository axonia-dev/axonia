import { type SyntheticEvent, useState } from "react";

import { FormError } from "@/components/patterns/FormError";
import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { DialogFooter } from "@/components/ui/DialogFooter";
import {
  useEditActionItem,
  useSettleActionItem,
} from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";
import type { ActionItemFormValue } from "@/features/events/ui/ActionItemFields";
import {
  ActionItemFields,
  actionItemFormValueOf,
  actionItemInputOf,
} from "@/features/events/ui/ActionItemFields";

interface Props {
  slug: string;
  item: ActionItem;
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Correcting an item already raised: the wrong assignee, the wrong due date, a typo in the title,
 * and the session it is answered at. Retargeting is what makes carry survivable - carry returns an
 * item to "next session", and this is the only way to point it at a chosen one instead.
 */
export function EditActionItemDialog({
  slug,
  item,
  isOpen,
  onOpenChange,
}: Readonly<Props>) {
  return (
    <Dialog
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      title="Edit action point"
    >
      <EditActionItemForm
        slug={slug}
        item={item}
        onDone={() => onOpenChange(false)}
      />
    </Dialog>
  );
}

interface FormProps {
  slug: string;
  item: ActionItem;
  onDone: () => void;
}

function EditActionItemForm({ slug, item, onDone }: Readonly<FormProps>) {
  const [value, setValue] = useState<ActionItemFormValue>(
    actionItemFormValueOf(item),
  );

  const edit = useEditActionItem(slug);
  const settle = useSettleActionItem(
    slug,
    item.id,
    "Saved the change, but could not update the status.",
  );
  const disabled = value.title.trim() === "";

  /** The edit lands first, because the backend refuses an update to a settled row: settling
   *  first would make the correction that came with it unsaveable. */
  function handleSubmit(event: SyntheticEvent) {
    event.preventDefault();
    edit.mutate(
      { id: item.id, input: actionItemInputOf(value) },
      {
        onSuccess: () => {
          if (value.outcome === item.outcome) {
            onDone();
            return;
          }
          settle.mutate(value.outcome, { onSuccess: onDone });
        },
      },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <ActionItemFields
        slug={slug}
        value={value}
        onChange={setValue}
        showStatus
      />
      <FormError error={edit.isError} />
      <DialogFooter>
        <Button variant="ghost" size="sm" onPress={onDone}>
          Cancel
        </Button>
        <Button type="submit" size="sm" isDisabled={disabled || edit.isPending}>
          Save
        </Button>
      </DialogFooter>
    </form>
  );
}
