import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

interface PageHeaderProps {
  title: string;
  action?: ReactNode;
  className?: string;
}

export function PageHeader({
  title,
  action,
  className,
}: Readonly<PageHeaderProps>) {
  return (
    // Stacked on a phone, side by side from the first breakpoint up. Kept here rather than at a
    // call site because every page with an action meets the same squeeze: at 390px a two-word
    // title wraps into whatever half the button leaves it, and a destructive action ends up
    // competing with the page title on the smallest screen there is. `items-start` holds in both
    // directions, so the action keeps its own width instead of stretching across the column.
    <header
      className={cn(
        "flex flex-col items-start gap-3 sm:flex-row sm:justify-between sm:gap-4",
        className,
      )}
    >
      <h1 className="font-display text-foreground text-2xl font-bold tracking-tight">
        {title}
      </h1>
      {action}
    </header>
  );
}
