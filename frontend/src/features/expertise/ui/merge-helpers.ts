import { CSS, type Transform } from "@dnd-kit/utilities";

import type { ExpertiseArea } from "@/features/expertise/domain/expertise";

/** The slice of a DOM rect the absorb math needs. */
type RectLike = { left: number; top: number; width: number; height: number };

const SHRUNK = { scaleX: 0.32, scaleY: 0.32 };

/**
 * Keyframes for the drag-merge "absorb": the floating overlay flies from where
 * it was dropped into the target card's centre while shrinking and fading, so it
 * reads as being pulled in and soaked up. With no target rect (e.g. an empty
 * drop, or jsdom with no layout) it falls back to collapsing in place.
 */
export function absorbKeyframes(
  from: Transform,
  overlayRect: RectLike | null,
  targetRect: RectLike | null,
): Keyframe[] {
  const start = { opacity: 1, transform: CSS.Transform.toString(from) };
  if (!targetRect || !overlayRect) {
    return [
      start,
      { opacity: 0, transform: CSS.Transform.toString({ ...from, ...SHRUNK }) },
    ];
  }
  const dx =
    targetRect.left +
    targetRect.width / 2 -
    (overlayRect.left + overlayRect.width / 2);
  const dy =
    targetRect.top +
    targetRect.height / 2 -
    (overlayRect.top + overlayRect.height / 2);
  const landed = { x: from.x + dx, y: from.y + dy, ...SHRUNK };
  return [start, { opacity: 0, transform: CSS.Transform.toString(landed) }];
}

/** The selected tag the most people can be asked about: the merge survivor
 *  default. Unknown tags count as zero; ties keep input order. */
export function defaultSurvivor(
  selected: string[],
  areas: ExpertiseArea[],
): string | null {
  if (selected.length === 0) return null;
  const countOf = (tag: string) =>
    areas.find((a) => a.canonicalTag === tag)?.peopleCount ?? 0;
  return selected.reduce<string>(
    (best, tag) => (countOf(tag) > countOf(best) ? tag : best),
    selected[0] ?? "",
  );
}

/** A drop of `activeId` onto `overId`: the drop target becomes canonical.
 *  Null when there is no target or it is the same tag. */
export function resolveMerge(
  activeId: string,
  overId: string | null,
): { variant: string; canonical: string } | null {
  if (!overId || overId === activeId) return null;
  return { variant: activeId, canonical: overId };
}
