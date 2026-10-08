import type { ReactNode } from "react";

import type { TrendDirection } from "@/features/health/domain/health";
import { cn } from "@/lib/cn";

const ARROW: Record<TrendDirection, string> = { UP: "↑", DOWN: "↓", FLAT: "→" };
const TONE: Record<TrendDirection, string> = {
  UP: "text-success",
  DOWN: "text-danger",
  FLAT: "text-muted-foreground",
};

export function TrendChip({
  direction,
  children,
}: Readonly<{ direction: TrendDirection; children: ReactNode }>) {
  return (
    <span className={cn("shrink-0 text-xs", TONE[direction])}>
      {ARROW[direction]} {children}
    </span>
  );
}
