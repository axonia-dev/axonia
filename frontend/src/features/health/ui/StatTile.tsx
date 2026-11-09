import type { ReactNode } from "react";
import { useId } from "react";

import type { Trend } from "@/features/health/domain/health";
import { TrendNote } from "@/features/health/ui/TrendNote";

export function StatTile({
  label,
  value,
  trend,
  children,
}: Readonly<{
  label: string;
  value: number;
  trend: Trend;
  children?: ReactNode;
}>) {
  const labelId = useId();
  return (
    <article
      aria-labelledby={labelId}
      className="border-border bg-surface flex min-w-0 flex-col items-start rounded-lg border px-3.5 py-5 sm:px-[22px]"
    >
      <p
        id={labelId}
        className="text-muted-foreground text-[11px] font-semibold tracking-[0.08em] uppercase"
      >
        {label}
      </p>
      <div className="mt-2.5 mb-2 flex flex-wrap items-baseline gap-x-2 gap-y-1">
        <span className="font-display text-foreground text-[40px] leading-none font-semibold">
          {value}
        </span>
        <TrendNote current={value} trend={trend} />
      </div>
      {children}
    </article>
  );
}
