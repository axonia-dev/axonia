export interface Point {
  x: number;
  y: number;
}

const FADE_FROM = 0.88;
const SCALE_FROM = 1.1;
const SCALE_TO = 0.8;

export function cometControl(from: Point, to: Point): Point {
  const distance = Math.hypot(to.x - from.x, to.y - from.y);
  return {
    x: (from.x + to.x) / 2 + distance * 0.08,
    y: Math.min(from.y, to.y) - distance * 0.19,
  };
}

function along(from: Point, control: Point, to: Point, t: number): Point {
  const u = 1 - t;
  return {
    x: u * u * from.x + 2 * u * t * control.x + t * t * to.x,
    y: u * u * from.y + 2 * u * t * control.y + t * t * to.y,
  };
}

function opacityAt(t: number): number {
  if (t <= FADE_FROM) return 1;
  return Math.max(0, 1 - (t - FADE_FROM) / (1 - FADE_FROM));
}

export function cometKeyframes({
  from,
  to,
  size,
  steps = 24,
}: Readonly<{
  from: Point;
  to: Point;
  size: number;
  steps?: number;
}>): Keyframe[] {
  const control = cometControl(from, to);
  return Array.from({ length: steps + 1 }, (_, i) => {
    const t = i / steps;
    const p = along(from, control, to, t);
    const scale =
      Math.round((SCALE_FROM + (SCALE_TO - SCALE_FROM) * t) * 1000) / 1000;
    return {
      offset: t,
      transform: `translate(${p.x - size / 2}px, ${p.y - size / 2}px) scale(${scale})`,
      opacity: opacityAt(t),
    };
  });
}
