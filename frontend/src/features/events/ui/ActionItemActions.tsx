import { Pencil, Trash2 } from "lucide-react";
import { useState } from "react";

import { IconButton } from "@/components/ui/IconButton";
import type { ActionItem } from "@/features/events/domain/action-item";
import { DeleteActionItemPopover } from "@/features/events/ui/DeleteActionItemPopover";
import { EditActionItemDialog } from "@/features/events/ui/EditActionItemDialog";

type Panel = "edit" | "delete" | null;

interface Props {
  slug: string;
  item: ActionItem;
}

interface TriggersProps {
  item: ActionItem;
  onOpen: (panel: Panel) => void;
}

/** Edit and delete only. Settling an item - done or dropped - is the row's own cycling control,
 *  not an icon here: they are the same decision, so they are one affordance. */
function Triggers({ item, onOpen }: Readonly<TriggersProps>) {
  return (
    <div className="flex shrink-0 gap-1 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
      {item.canEdit && (
        <IconButton label={`Edit ${item.title}`} onPress={() => onOpen("edit")}>
          <Pencil />
        </IconButton>
      )}
      {item.canDelete && (
        <IconButton
          tone="danger"
          label={`Delete ${item.title}`}
          onPress={() => onOpen("delete")}
        >
          <Trash2 />
        </IconButton>
      )}
    </div>
  );
}

/**
 * Correct or remove one action item, from wherever its row is drawn.
 *
 * The two are different answers, not two shades of one: an edit fixes a record that still
 * stands, a delete takes back an item raised by mistake. Settling an item - done or dropped - is
 * the row's own cycling control, not a panel here. Each is gated by the permission the backend
 * asks for on that route, never by a role check invented here.
 *
 * The popover anchors to the nearest positioned ancestor, so the host row carries `relative`, and
 * it mounts only while open so a row nobody acts on holds no mutation of its own.
 */
export function ActionItemActions({ slug, item }: Readonly<Props>) {
  const [panel, setPanel] = useState<Panel>(null);

  if (!item.canEdit && !item.canDelete) {
    return null;
  }

  function close() {
    setPanel(null);
  }

  return (
    <>
      <Triggers item={item} onOpen={setPanel} />
      {item.canEdit && (
        <EditActionItemDialog
          slug={slug}
          item={item}
          isOpen={panel === "edit"}
          onOpenChange={(open) => setPanel(open ? "edit" : null)}
        />
      )}
      {panel === "delete" && (
        <DeleteActionItemPopover slug={slug} item={item} onDone={close} />
      )}
    </>
  );
}
