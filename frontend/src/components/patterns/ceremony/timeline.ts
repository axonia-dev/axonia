export interface Beat {
  readonly start: number;
  readonly duration: number;
}

export function progress(beat: Beat, t: number): number {
  if (beat.duration <= 0) {
    return t >= beat.start ? 1 : 0;
  }
  return Math.min(1, Math.max(0, (t - beat.start) / beat.duration));
}

export function easeInOutCubic(x: number): number {
  return x < 0.5 ? 4 * x ** 3 : 1 - (-2 * x + 2) ** 3 / 2;
}

export function easeOutCubic(x: number): number {
  return 1 - (1 - x) ** 3;
}

export function easeOutBack(x: number): number {
  const overshoot = 1.70158;
  return 1 + (overshoot + 1) * (x - 1) ** 3 + overshoot * (x - 1) ** 2;
}

export function lerp(from: number, to: number, p: number): number {
  return from + (to - from) * p;
}
