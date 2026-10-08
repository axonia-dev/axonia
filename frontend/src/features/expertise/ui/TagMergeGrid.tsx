import {
  DndContext,
  type DragEndEvent,
  DragOverlay,
  type DropAnimation,
  useSensors,
} from "@dnd-kit/core";
import { useRef, useState } from "react";

import type { ExpertiseArea } from "@/features/expertise/domain/expertise";
import {
  absorbKeyframes,
  resolveMerge,
} from "@/features/expertise/ui/merge-helpers";
import { MergeableTagCard } from "@/features/expertise/ui/MergeableTagCard";
import { type MergeHint, TagCard } from "@/features/expertise/ui/TagCard";

const NOOP = () => {};

const prefersReducedMotion = () =>
  typeof window !== "undefined" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

interface Props {
  areas: ExpertiseArea[];
  selected: Set<string>;
  survivor: string;
  onToggleSelect: (tag: string) => void;
  onSplit: (variant: string, canonical: string) => void;
  onDragEnd: (event: DragEndEvent) => void;
  sensors: ReturnType<typeof useSensors>;
  onRename?: ((tag: string) => void) | undefined;
  onDelete?: ((tag: string) => void) | undefined;
}

function hintFor(
  area: ExpertiseArea,
  isSelected: boolean,
  isBatch: boolean,
  survivor: string,
): MergeHint | undefined {
  if (!isBatch || !isSelected) return undefined;
  if (area.canonicalTag === survivor) return { kind: "keep" };
  return { kind: "into", target: survivor };
}

/**
 * The drag-and-drop grid of tag cards. Owns the DndContext, the active-drag
 * state, and the floating DragOverlay. Dragging one card onto another merges it
 * (see resolveMerge); the drop "absorbs" into the target. Dragging is disabled
 * whenever a checkbox selection is in progress so the two merge flows can't
 * collide.
 */
export function TagMergeGrid({
  areas,
  selected,
  survivor,
  onToggleSelect,
  onSplit,
  onDragEnd,
  sensors,
  onRename,
  onDelete,
}: Readonly<Props>) {
  const [activeId, setActiveId] = useState<string | null>(null);
  const [absorbingId, setAbsorbingId] = useState<string | null>(null);
  // Drives the drop animation: a real merge "absorbs" into the target, an
  // empty drop snaps back. Set alongside setActiveId(null) so the same render
  // that ends the drag picks the right animation.
  const [didMerge, setDidMerge] = useState(false);
  // The just-merged variant and its new home, so the survivor flashes the
  // freshly-landed alias chip once the merge refetches (TagCard chip-land).
  const [justMerged, setJustMerged] = useState<{
    variant: string;
    canonical: string;
  } | null>(null);
  // The drop target's droppable id, read by the absorb keyframes (at drop time,
  // not during render) to fly the overlay into that card's centre.
  const dropTargetId = useRef<string | null>(null);
  const activeArea = areas.find((a) => a.canonicalTag === activeId) ?? null;
  const selectionActive = selected.size > 0;
  const isBatch = selected.size >= 2;

  const absorb: DropAnimation = {
    duration: 460,
    easing: "cubic-bezier(0.55, 0, 0.85, 0.2)",
    keyframes: ({ transform, dragOverlay, droppableContainers }) => {
      const targetId = dropTargetId.current;
      const targetRect = targetId
        ? (droppableContainers.get(targetId)?.rect.current ?? null)
        : null;
      return absorbKeyframes(transform.initial, dragOverlay.rect, targetRect);
    },
  };

  function handleDragEnd(event: DragEndEvent) {
    const move = resolveMerge(
      String(event.active.id),
      event.over ? String(event.over.id) : null,
    );
    setDidMerge(move !== null);
    dropTargetId.current = move ? move.canonical : null;
    // Reduced motion plays no pulse / landing, so their animationend never fires
    // to clear them, so don't set them at all in that case (matches dropAnimation).
    // Otherwise always assign (null on an empty drop) so a prior merge's pulse /
    // landing can't linger on its old target.
    const animated = prefersReducedMotion() ? null : move;
    setAbsorbingId(animated ? animated.canonical : null);
    setJustMerged(
      animated
        ? { variant: animated.variant, canonical: animated.canonical }
        : null,
    );
    setActiveId(null);
    onDragEnd(event);
  }

  // Reduced motion skips the flight entirely; otherwise a real merge absorbs and
  // an empty drop snaps back (the dnd-kit default).
  let dropAnimation: DropAnimation | null | undefined;
  if (prefersReducedMotion()) dropAnimation = null;
  else if (didMerge) dropAnimation = absorb;

  return (
    <DndContext
      sensors={sensors}
      onDragStart={(event) => {
        // Clear any leftover pulse / landing from a previous merge.
        setAbsorbingId(null);
        setJustMerged(null);
        setActiveId(String(event.active.id));
      }}
      onDragEnd={handleDragEnd}
      onDragCancel={() => setActiveId(null)}
    >
      <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
        {areas.map((area) => {
          const isSelected = selected.has(area.canonicalTag);
          const hint = hintFor(area, isSelected, isBatch, survivor);
          const landedVariant =
            justMerged?.canonical === area.canonicalTag
              ? justMerged.variant
              : undefined;
          return (
            <MergeableTagCard
              key={area.canonicalTag}
              area={area}
              selected={isSelected}
              onToggleSelect={onToggleSelect}
              onSplit={(variant) => onSplit(variant, area.canonicalTag)}
              draggable={!selectionActive}
              hint={hint}
              absorbing={absorbingId === area.canonicalTag}
              onAbsorbEnd={() => setAbsorbingId(null)}
              landedVariant={landedVariant}
              onRename={onRename}
              onDelete={onDelete}
            />
          );
        })}
      </div>
      <DragOverlay dropAnimation={dropAnimation}>
        {activeArea ? (
          <TagCard
            canonicalTag={activeArea.canonicalTag}
            variants={activeArea.variants}
            onSplit={NOOP}
          />
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}
