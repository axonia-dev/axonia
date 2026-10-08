import { useMemo } from "react";

import type { MapContribution } from "@/features/expertise/domain/expertise";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import type { Spot } from "@/features/expertise/ui/terrain-projection";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";

/** WCAG 2.2 SC 2.5.8's 24px floor, as a radius. A mark is often smaller than its target, and the target is what a
 *  pointer and a keyboard get. */
const MIN_HIT_RADIUS = 12;

/**
 * The ceiling on how many buttons the overlay draws. What a dense area actually yields is decided below, by
 * whether a target fits: the readings panel beside the map carries every fact whatever the map draws.
 */
export const MAX_OVERLAY_BUTTONS = 400;

type SourceType = MapContribution["evidence"]["sourceType"];

const KINDS: SourceType[] = ["SLOT", "ARTIFACT", "TOPIC", "ACTION_ITEM"];

/**
 * The budget is spent round-robin across the four kinds, each of them in id order. Spending it on one ranking would
 * hand every button to the talks, because only a slot has a reach and a community has hundreds of them: an artifact
 * would never be reachable at all. Id order also keeps the tab sequence free of any ranking of people.
 */
function withinBudget(marks: PlacedContribution[]): PlacedContribution[] {
  const buckets = KINDS.map((kind) =>
    marks
      .filter((mark) => mark.sourceType === kind)
      .sort((a, b) => a.signalId - b.signalId),
  );

  const kept: PlacedContribution[] = [];
  const deepest = Math.max(...buckets.map((bucket) => bucket.length));
  for (let rank = 0; rank < deepest; rank++) {
    for (const bucket of buckets) {
      const mark = bucket[rank];
      if (mark !== undefined && kept.length < MAX_OVERLAY_BUTTONS) {
        kept.push(mark);
      }
    }
  }
  return kept;
}

interface At {
  x: number;
  y: number;
}

function cellOf(at: At, step: number): string {
  return `${Math.floor(at.x / step)}:${Math.floor(at.y / step)}`;
}

function disturbs(taken: Map<string, At[]>, at: At, step: number): boolean {
  const col = Math.floor(at.x / step);
  const row = Math.floor(at.y / step);

  for (let dc = -1; dc <= 1; dc++) {
    for (let dr = -1; dr <= 1; dr++) {
      const near = taken.get(`${col + dc}:${row + dr}`) ?? [];
      if (near.some((o) => Math.hypot(o.x - at.x, o.y - at.y) < step)) {
        return true;
      }
    }
  }
  return false;
}

interface Placed {
  mark: PlacedContribution;
  spot: Spot;
}

/**
 * Two focus rings that overlap are not two targets a reader can tell apart: tabbing moves the ring to a place it
 * already appeared to be. A dense area can put hundreds of marks inside one 24px circle, so a mark joins the tab
 * sequence only where a whole undisturbed circle fits. The rest of that area is reachable through the readings
 * panel, which lists every fact whatever the map draws, and through the pointer, which picks the nearest mark
 * rather than the one that got a button.
 *
 * The grid makes it one pass: a target can only be disturbed by one in its own cell or in a neighbouring one,
 * because every cell is exactly one diameter across.
 */
function reachable(
  marks: PlacedContribution[],
  spotOf: (mark: PlacedContribution) => Spot | null,
  size: GraphSize,
): Placed[] {
  const step = MIN_HIT_RADIUS * 2;
  const taken = new Map<string, At[]>();
  const kept: Placed[] = [];

  for (const mark of marks) {
    const spot = spotOf(mark);
    if (spot === null) continue;

    const at = { x: spot.left * size.width, y: spot.top * size.height };
    if (disturbs(taken, at, step)) continue;

    const cell = cellOf(at, step);
    taken.set(cell, [...(taken.get(cell) ?? []), at]);
    kept.push({ mark, spot });
  }

  return kept;
}

const TARGET =
  "focus-visible:ring-focus-ring pointer-events-none absolute -translate-x-1/2 -translate-y-1/2 rounded-full focus-visible:ring-2 focus-visible:outline-none";

/**
 * The drawing is aria-hidden, so these buttons are how a keyboard or a screen reader reaches a mark: an aria-hidden
 * element cannot hold a focusable descendant, which is why they live in a sibling of the canvas.
 *
 * They are deliberately deaf to the pointer. A pointer is served by the drawing itself, which picks the nearest
 * mark to where it was aimed; a 24px button would steal that click and hand back whichever of two superposed marks
 * happened to get the button, which is exactly the thing a reader zooms in to stop happening.
 *
 * It takes the projection as a callback rather than a surface and a camera, so the terrain and the stream reach
 * their marks through the one overlay: each reading hands over the function that places its own marks, and nothing
 * here knows which one drew them.
 *
 * Which mark is chosen is said here only in `aria-current`. A ring drawn around it used to be the one cue on the
 * drawing, and a crowded area never got it: a target is given only where a whole undisturbed one fits, so a click
 * on the very areas dense enough to want a selection showed nothing at all. The drawing paints the chosen mark
 * itself now, wherever it stands.
 */
export function ContributionOverlay({
  spotOf,
  size,
  marks,
  labels,
  selectedId,
  onSelect,
}: Readonly<{
  spotOf: (mark: PlacedContribution) => Spot | null;
  size: GraphSize;
  marks: PlacedContribution[];
  labels: ReadonlyMap<number, string>;
  selectedId: number | null;
  onSelect: (signalId: number) => void;
}>) {
  // Dragging the globe re-renders this on every pointer event, and the budget is a filter and a sort over every
  // mark the payload carried - up to five thousand of them - while what it answers changes only when they do.
  const budget = useMemo(() => withinBudget(marks), [marks]);
  const visible = reachable(budget, spotOf, size);

  const width = ((MIN_HIT_RADIUS * 2) / size.width) * 100;
  const height = ((MIN_HIT_RADIUS * 2) / size.height) * 100;

  return (
    // Transparent to the pointer, targets included: every press belongs to the drawing underneath.
    <div className="pointer-events-none absolute inset-0">
      {visible.map(({ mark, spot }) => (
        <button
          key={mark.signalId}
          type="button"
          onClick={() => onSelect(mark.signalId)}
          aria-current={mark.signalId === selectedId}
          style={{
            left: `${spot.left * 100}%`,
            top: `${spot.top * 100}%`,
            width: `${width}%`,
            height: `${height}%`,
          }}
          className={TARGET}
        >
          <span className="sr-only">
            {labels.get(mark.signalId) ?? mark.tag}
          </span>
        </button>
      ))}
    </div>
  );
}
