import { Pencil, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/Button";

interface Props {
  canManageSlot: boolean;
  removePending: boolean;
  onEdit: () => void;
  onRemove: () => void;
}

/** The hover/focus-revealed edit/remove cluster for a slot row. Presenter membership
 * (join, invite, withdraw, remove) lives on the avatar stack, not here. */
export function SlotActions({
  canManageSlot,
  removePending,
  onEdit,
  onRemove,
}: Readonly<Props>) {
  if (!canManageSlot) {
    return null;
  }
  return (
    <span className="ml-auto flex items-center gap-1.5 opacity-0 transition-opacity group-focus-within:opacity-100 group-hover:opacity-100 motion-reduce:transition-none @max-md:col-start-2 @max-md:row-start-1 [@media(hover:none)]:opacity-100">
      <Button
        variant="outline"
        size="sm"
        aria-label="Edit slot"
        className="hover:border-primary hover:text-primary"
        onPress={onEdit}
      >
        <Pencil className="h-3.5 w-3.5" />
      </Button>
      <Button
        variant="outline"
        size="sm"
        aria-label="Remove slot"
        className="hover:border-danger hover:text-danger"
        isDisabled={removePending}
        onPress={onRemove}
      >
        <Trash2 className="h-3.5 w-3.5" />
      </Button>
    </span>
  );
}
