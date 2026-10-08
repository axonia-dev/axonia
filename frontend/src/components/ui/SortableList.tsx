import type { DragEndEvent, DraggableSyntheticListeners } from "@dnd-kit/core";
import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import type { ReactNode } from "react";

/**
 * Everything the consumer spreads onto its grip element: dnd-kit drag listeners
 * (pointer/keyboard events) plus accessibility attributes (role, tabIndex, …).
 * Both must land on the same focusable element so keyboard navigation and ARIA
 * semantics are correct.
 */
type Handle = ReturnType<typeof useSortable>["attributes"] &
  DraggableSyntheticListeners;

interface SortableListProps {
  ids: string[];
  onReorder: (next: string[]) => void;
  children: (id: string, handle: Handle) => ReactNode;
}

/**
 * Pure reorder mapping: applies a drag from `activeId` onto `overId` against the
 * current `ids`, returning the next order. Returns `null` when the move is a
 * no-op (same id, or either id is missing) so callers can skip the callback.
 */
export function reorderByIds(
  ids: string[],
  activeId: string,
  overId: string,
): string[] | null {
  if (activeId === overId) {
    return null;
  }
  const from = ids.indexOf(activeId);
  const to = ids.indexOf(overId);
  if (from === -1 || to === -1) {
    return null;
  }
  return arrayMove(ids, from, to);
}

function SortableItem({
  id,
  render,
}: Readonly<{
  id: string;
  render: (id: string, handle: Handle) => ReactNode;
}>) {
  const { attributes, listeners, setNodeRef, transform, transition } =
    useSortable({ id });
  const style = { transform: CSS.Transform.toString(transform), transition };
  return (
    <li ref={setNodeRef} style={style}>
      {render(id, { ...attributes, ...listeners } as Handle)}
    </li>
  );
}

export function SortableList({
  ids,
  onReorder,
  children,
}: Readonly<SortableListProps>) {
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over) {
      return;
    }
    const next = reorderByIds(ids, String(active.id), String(over.id));
    if (next) {
      onReorder(next);
    }
  }

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={closestCenter}
      onDragEnd={handleDragEnd}
    >
      <SortableContext items={ids} strategy={verticalListSortingStrategy}>
        <ul className="flex flex-col gap-2">
          {ids.map((id) => (
            <SortableItem key={id} id={id} render={children} />
          ))}
        </ul>
      </SortableContext>
    </DndContext>
  );
}
