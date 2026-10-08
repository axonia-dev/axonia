const HALF = 0.5;
const TOLERANCE = 1e-9;

/**
 * How many segments it takes to reach half of all contribution. Mirrors the backend's
 * Distribution.busFactor exactly, tolerance included, so the drawn crossing and the printed number
 * can never disagree.
 */
export function carriersOf(distribution: number[]): number {
  let cumulative = 0;
  for (let index = 0; index < distribution.length; index++) {
    cumulative += distribution[index] ?? 0;
    if (cumulative >= HALF - TOLERANCE) {
      return index + 1;
    }
  }
  return distribution.length;
}

/**
 * One segment per contributor, widest first, with the halfway line drawn across it. The bus factor
 * is how many segments it takes to cross that line, so the graphic states its own definition.
 *
 * No per-segment label, no title, no hover: position is the only identity a segment has, which is
 * the aggregates-only rule expressed as a rendering constraint rather than a promise.
 */
export function LoadBearingStack({
  distribution,
  tone,
}: Readonly<{ distribution: number[]; tone: string }>) {
  if (distribution.length === 0) {
    return (
      <p className="text-muted-foreground mt-5 text-sm">
        Nobody contributed in this window.
      </p>
    );
  }
  const carriers = carriersOf(distribution);
  const top3 = distribution.slice(0, 3).reduce((sum, share) => sum + share, 0);

  return (
    <div className="mt-6">
      <div className="relative">
        <div
          aria-label="Anonymous contribution distribution"
          className="bg-surface-sunken flex h-11 gap-0.5 overflow-hidden rounded-md"
        >
          {distribution.map((share, index) => (
            // anonymous by design: position is the only identity a segment has
            <div
              key={index}
              className="h-full"
              style={{
                flex: `0 0 ${(share * 100).toFixed(2)}%`,
                background:
                  index < carriers
                    ? `linear-gradient(180deg, color-mix(in srgb, ${tone} 58%, white), ${tone})`
                    : `color-mix(in srgb, ${tone} 26%, var(--surface-sunken))`,
              }}
            />
          ))}
        </div>
        <div
          aria-hidden
          className="border-foreground/45 pointer-events-none absolute -top-2 bottom-[-6px] left-1/2 w-0 border-l-2 border-dashed"
        />
        <p className="text-muted-foreground absolute -top-6 left-[calc(50%+8px)] text-[10px] font-bold tracking-wider uppercase max-sm:left-0">
          half of all contribution
        </p>
      </div>
      <div className="text-muted-foreground mt-7 flex flex-wrap justify-between gap-4 text-sm">
        <span>
          <b className="text-foreground">{carriers}</b>{" "}
          {carriers === 1 ? "person carries" : "people carry"} the first half
          {" · "}
          {distribution.length} contributed in total
        </span>
        <span>top 3 carry {Math.round(top3 * 100)}%</span>
      </div>
    </div>
  );
}
