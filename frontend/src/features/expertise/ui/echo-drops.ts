import { type Drop, ECHO } from "@/features/expertise/ui/echo-timeline";

export interface DropSource {
  signalId: number;
  tag: string;
  slot: number;
  x: number;
  y: number;
}

/**
 * Every echo starts where an answer is: one drop per area the lit marks stand on, from their centre. An area matched
 * by name lights every mark on it, so the same rule covers a name match and a title or author match.
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
  return groups.map(([, group], index) => ({
    x: group.reduce((sum, m) => sum + m.x, 0) / group.length,
    y: group.reduce((sum, m) => sum + m.y, 0) / group.length,
    delayMs: index * ECHO.staggerMs,
    slot: group[0]!.slot,
  }));
}
