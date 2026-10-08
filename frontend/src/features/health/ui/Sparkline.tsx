import { useId } from "react";

const WIDTH = 100;
const HEIGHT = 30;
const PADDING = 2;

/**
 * Shape only: no axes, no grid, no numbers. The value beside it carries the number.
 *
 * Hand-rolled rather than pulled from a charting library because a library brings its own colour
 * system, and this one has to read `--primary` while the community shell is remapping it.
 */
export function Sparkline({
  values,
  label,
  tone = "var(--primary)",
}: Readonly<{ values: number[]; label: string; tone?: string }>) {
  const gradientId = `sparkline-area-${useId()}`;
  if (values.length === 0) {
    return null;
  }
  const max = Math.max(...values);
  const min = Math.min(...values, 0);
  const span = max - min || 1;
  const step = values.length > 1 ? WIDTH / (values.length - 1) : 0;
  const coordinates = values.map((value, index) => {
    const y =
      HEIGHT - ((value - min) / span) * (HEIGHT - PADDING * 2) - PADDING;
    return { x: index * step, y };
  });
  const points = coordinates
    .map(({ x, y }) => `${x.toFixed(1)},${y.toFixed(1)}`)
    .join(" ");
  const lastX = (coordinates.at(-1)?.x ?? 0).toFixed(1);
  const firstX = (coordinates[0]?.x ?? 0).toFixed(1);
  const areaPoints = `${points} ${lastX},${HEIGHT} ${firstX},${HEIGHT}`;
  const last = coordinates.at(-1);

  return (
    <svg
      role="img"
      aria-label={label}
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="none"
      className="mt-3 mb-0.5 block h-[30px] w-full overflow-visible"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={tone} stopOpacity=".28" />
          <stop offset="100%" stopColor={tone} stopOpacity="0" />
        </linearGradient>
      </defs>
      <polygon points={areaPoints} fill={`url(#${gradientId})`} />
      <polyline
        points={points}
        fill="none"
        stroke={tone}
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      />
      {last ? (
        <circle
          cx={last.x.toFixed(1)}
          cy={last.y.toFixed(1)}
          r="2.2"
          fill={tone}
        />
      ) : null}
    </svg>
  );
}
