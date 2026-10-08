import { plural } from "@/lib/plural";

const WIDTH = 600;
const HEIGHT = 76;
const BASELINE = HEIGHT - 22;
const DAY_MS = 86_400_000;

/** Each held session's position across the window as a fraction. Anything outside is dropped. */
export function positionsOf(
  dates: string[],
  windowDays: number,
  generatedAt: string,
): number[] {
  const end = Date.parse(generatedAt);
  const start = end - windowDays * DAY_MS;
  return dates
    .map((date) => (Date.parse(date) - start) / (end - start))
    .filter((fraction) => fraction >= 0 && fraction <= 1);
}

/**
 * One spike per session that was really held, at its real date. A steady rhythm reads as an even
 * comb, slowing reads as spikes bunching left, dormant reads as a flat line, none of which needs a
 * status word to say it. The status word is still shown; it is no longer the only carrier.
 */
export function CadencePulse({
  heldSessionDates,
  windowDays,
  generatedAt,
}: Readonly<{
  heldSessionDates: string[];
  windowDays: number;
  generatedAt: string;
}>) {
  const positions = positionsOf(heldSessionDates, windowDays, generatedAt);
  const ariaLabel = `${plural(positions.length, "session")} held over the window`;

  if (positions.length === 0) {
    return (
      <svg
        role="img"
        aria-label={ariaLabel}
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        preserveAspectRatio="none"
        className="block h-[76px] w-full"
      >
        <line
          x1="0"
          y1={BASELINE}
          x2={WIDTH}
          y2={BASELINE}
          stroke="var(--border-strong)"
          strokeWidth="1.5"
          strokeDasharray="4 6"
          vectorEffect="non-scaling-stroke"
        />
      </svg>
    );
  }

  const path = positions.reduce((d, fraction) => {
    const x = fraction * WIDTH;
    return `${d} L${(x - 11).toFixed(1)} ${BASELINE} L${(x - 5).toFixed(1)} 16 L${x.toFixed(1)} ${BASELINE - 4} L${(x + 6).toFixed(1)} ${BASELINE}`;
  }, `M0 ${BASELINE}`);

  return (
    <svg
      role="img"
      aria-label={ariaLabel}
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="none"
      className="block h-[76px] w-full"
    >
      <line
        x1="0"
        y1={BASELINE}
        x2={WIDTH}
        y2={BASELINE}
        stroke="var(--border)"
        strokeWidth="1"
        vectorEffect="non-scaling-stroke"
      />
      <path
        d={`${path} L${WIDTH} ${BASELINE}`}
        fill="none"
        stroke="var(--primary)"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
      {positions.map((fraction, index) => (
        <circle
          key={index}
          cx={(fraction * WIDTH).toFixed(1)}
          cy={BASELINE}
          r="3"
          fill="var(--primary)"
        />
      ))}
    </svg>
  );
}
