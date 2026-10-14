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
 * Every echo starts where an answer is: one drop per area the lit marks stand on, from their centre. An area matched
 * by name lights every mark on it, so the same rule covers a name match and a title or author match. Past
 * {@link MAX_DROPS} only the biggest areas drop: the rest stay lit, and the nearest echo carries them.
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
  return groups.slice(0, MAX_DROPS).map(([, group], index) => ({
    x: group.reduce((sum, m) => sum + m.x, 0) / group.length,
    y: group.reduce((sum, m) => sum + m.y, 0) / group.length,
    delayMs: index * ECHO.staggerMs,
    slot: group[0]!.slot,
  }));
}
