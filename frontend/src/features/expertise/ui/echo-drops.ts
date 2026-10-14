import { type Drop, ECHO } from "@/features/expertise/ui/echo-timeline";

export interface DropSource {
  signalId: number;
  tag: string;
  slot: number;
  x: number;
  y: number;
}

/** More drops than this is a term matching most of the map, where one more ring says nothing the others have not. */
export const MAX_DROPS = 24;

/**
 * The lit mark nearest the centre of an area's lit marks. The centre itself can lie off the area: a stream band
 * curves, and the middle of its marks falls in the gap beside it, where a ring cut to the bands would start unseen.
 */
function nearestTheCentre(group: readonly DropSource[]): DropSource {
  const cx = group.reduce((sum, m) => sum + m.x, 0) / group.length;
  const cy = group.reduce((sum, m) => sum + m.y, 0) / group.length;
  let nearest = group[0]!;
  let best = Infinity;
  for (const mark of group) {
    const distance = (mark.x - cx) ** 2 + (mark.y - cy) ** 2;
    if (distance < best) {
      best = distance;
      nearest = mark;
    }
  }
  return nearest;
}

/**
 * Every echo starts where an answer is: one drop per area the lit marks stand on, from the one nearest their centre.
 * An area matched by name lights every mark on it, so the same rule covers a name match and a title or author match.
 * Past {@link MAX_DROPS} only the biggest areas drop: the rest stay lit, and the nearest echo carries them.
 */
export function dropsFor(
  marks: readonly DropSource[],
  lit: ReadonlySet<number>,
): Drop[] {
  const byTag = new Map<string, DropSource[]>();
  for (const mark of marks) {
    if (!lit.has(mark.signalId)) continue;
    const group = byTag.get(mark.tag);
    if (group === undefined) byTag.set(mark.tag, [mark]);
    else group.push(mark);
  }
  const groups = [...byTag.entries()];
  groups.sort(([a, x], [b, y]) => y.length - x.length || a.localeCompare(b));
  return groups.slice(0, MAX_DROPS).map(([, group], index) => {
    const { x, y, slot } = nearestTheCentre(group);
    return { x, y, delayMs: index * ECHO.staggerMs, slot };
  });
}
