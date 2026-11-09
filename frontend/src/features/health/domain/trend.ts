import type { Trend } from "@/features/health/domain/health";

/**
 * Neutral on purpose: up is not always good, so judgement stays on the pill. Two empty windows
 * say nothing, which is what keeps "from 0" from reading as noise.
 */
export function trendText(current: number, trend: Trend): string | null {
  if (current === 0 && trend.previous === 0) return null;
  if (trend.direction === "UP") return `up from ${trend.previous}`;
  if (trend.direction === "DOWN") return `down from ${trend.previous}`;
  return "same as before";
}
