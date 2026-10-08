/** Clear air between two names, as the map keeps around its own; at the drawing's edges the card's padding gives it. */
const GUTTER = 4;
export const TOLERANCE = 1e-6;

interface Placed {
  top: number;
  left: number;
  width: number;
  height: number;
  visible: boolean;
}

interface Spec {
  reached: boolean;
  marks: number;
}

/** Strictly short of `limit`, so two names pushed exactly a gutter apart no longer count as touching. */
function shortOf(value: number, limit: number): boolean {
  return value < limit - TOLERANCE;
}

function overlaps(a: Placed, b: Placed): boolean {
  return (
    shortOf(a.left, b.left + b.width + GUTTER) &&
    shortOf(b.left, a.left + a.width + GUTTER) &&
    shortOf(a.top, b.top + b.height + GUTTER) &&
    shortOf(b.top, a.top + a.height + GUTTER)
  );
}

function firstCollision(
  labels: readonly Placed[],
): [Placed, Placed] | undefined {
  for (const [i, a] of labels.entries()) {
    const b = labels.slice(i + 1).find((other) => overlaps(a, other));
    if (b !== undefined) return [a, b];
  }
  return undefined;
}

function middleOf(label: Placed): number {
  return label.top + label.height / 2;
}

/** The name further from the centre line moves further out, straight up or down, so it never leaves the width. */
function pushApart(a: Placed, b: Placed): void {
  const outerIsA = Math.abs(middleOf(a)) > Math.abs(middleOf(b));
  const outer = outerIsA ? a : b;
  const inner = outerIsA ? b : a;
  outer.top =
    middleOf(outer) < 0
      ? inner.top - outer.height - GUTTER
      : inner.top + inner.height + GUTTER;
}

function separate(reached: readonly Placed[]): void {
  for (let pass = 0; pass < 4 * reached.length ** 2; pass++) {
    const pair = firstCollision(reached);
    if (pair === undefined) return;
    pushApart(...pair);
  }
}

/** Keeps an empty spoke's name only where it overlaps nothing shown: a marked name before an unmarked one, narrow first. */
export function keepClear(
  labels: readonly Placed[],
  specs: readonly Spec[],
): void {
  const reached = labels.filter((_, i) => specs.at(i)?.reached === true);
  separate(reached);
  const shown = [...reached];
  const rest = labels
    .map((label, i) => ({ label, marked: (specs.at(i)?.marks ?? 0) > 0 }))
    .filter(({ label }) => !reached.includes(label))
    .sort(
      (a, b) =>
        Number(b.marked) - Number(a.marked) || a.label.width - b.label.width,
    );
  for (const { label } of rest) {
    label.visible = !shown.some((other) => overlaps(other, label));
    if (label.visible) shown.push(label);
  }
}
