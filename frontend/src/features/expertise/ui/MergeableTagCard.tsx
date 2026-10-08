import { useDraggable, useDroppable } from "@dnd-kit/core";

import type { ExpertiseArea } from "@/features/expertise/domain/expertise";
import { type MergeHint, TagCard } from "@/features/expertise/ui/TagCard";
import { cn } from "@/lib/cn";

interface Props {
  area: ExpertiseArea;
  selected: boolean;
  onToggleSelect: (tag: string) => void;
  onSplit: (variant: string) => void;
  /** Drag is the no-selection mouse shortcut; disabled once a selection is in
   *  progress so the two merge flows never collide. */
  draggable: boolean;
  hint?: MergeHint | undefined;
  /** True for the drop target right after a drag-merge, to play the pulse. */
  absorbing: boolean;
  onAbsorbEnd: () => void;
  /** An alias that just merged into this card; lands with a one-shot highlight. */
  landedVariant?: string | undefined;
  onRename?: ((tag: string) => void) | undefined;
  onDelete?: ((tag: string) => void) | undefined;
}

/** A TagCard that can be dragged onto another card to merge into it, and is a
 *  drop target for such a drag. The floating copy that follows the cursor is
 *  the DragOverlay (in TagMergeGrid); here we dim the source while it is being
 *  dragged and pulse it when it absorbs a dropped tag. */
export function MergeableTagCard({
  area,
  selected,
  onToggleSelect,
  onSplit,
  draggable,
  hint,
  absorbing,
  onAbsorbEnd,
  landedVariant,
  onRename,
  onDelete,
}: Readonly<Props>) {
  const id = area.canonicalTag;
  const {
    setNodeRef: setDragRef,
    listeners,
    isDragging,
  } = useDraggable({ id, disabled: !draggable });
  const { setNodeRef: setDropRef, isOver } = useDroppable({ id });
  return (
    <div
      ref={setDropRef}
      // Only the wrapper's own merge-pulse clears the state, not a chip-land
      // animationend bubbling up from a descendant chip on the same card.
      onAnimationEnd={
        absorbing
          ? (event) => {
              if (event.target === event.currentTarget) onAbsorbEnd();
            }
          : undefined
      }
      className={cn(
        "rounded-xl",
        isOver && "ring-2 ring-[var(--primary)]",
        absorbing && "animate-merge-pulse",
      )}
    >
      <div
        ref={setDragRef}
        {...(draggable ? listeners : {})}
        className={cn(
          draggable && "touch-none",
          isDragging
            ? "cursor-grabbing opacity-40"
            : draggable && "cursor-grab",
        )}
      >
        <TagCard
          canonicalTag={area.canonicalTag}
          variants={area.variants}
          onSplit={onSplit}
          selected={selected}
          onToggleSelect={onToggleSelect}
          hint={hint}
          landedVariant={landedVariant}
          onRename={onRename}
          onDelete={onDelete}
        />
      </div>
    </div>
  );
}
