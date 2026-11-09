import type { Trend } from "@/features/health/domain/health";
import { trendText } from "@/features/health/domain/trend";
import { cn } from "@/lib/cn";

/** Neutral by design: a change is stated, never judged; the pill beside it judges. */
export function TrendNote({
  current,
  trend,
}: Readonly<{ current: number; trend: Trend }>) {
  const text = trendText(current, trend);
  if (text === null) return null;
  return (
    <span
      className={cn(
        "text-xs font-semibold",
        trend.direction === "FLAT"
          ? "text-muted-foreground"
          : "text-foreground/75",
      )}
    >
      {text}
    </span>
  );
}
