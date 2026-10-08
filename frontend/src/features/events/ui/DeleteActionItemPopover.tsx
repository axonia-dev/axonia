import { ConfirmPopover } from "@/components/patterns/ConfirmPopover";
import { useDeleteActionItem } from "@/features/events/api/use-action-items";
import type { ActionItem } from "@/features/events/domain/action-item";

interface Props {
  slug: string;
  item: ActionItem;
  onDone: () => void;
}

/** Taking back an item raised by mistake. The one destructive answer of the three, and the only
 *  one that leaves nothing behind - closing and dropping both keep a record. */
export function DeleteActionItemPopover({
  slug,
  item,
  onDone,
}: Readonly<Props>) {
  const remove = useDeleteActionItem(slug);

  return (
    <ConfirmPopover
      label="Confirm delete"
      isOpen={true}
      confirmLabel="Remove"
      isPending={remove.isPending}
      error={remove.isError}
      onConfirm={() => remove.mutate(item.id, { onSuccess: onDone })}
      onCancel={onDone}
    >
      Remove <strong>{item.title}</strong>? It leaves no record.
    </ConfirmPopover>
  );
}
