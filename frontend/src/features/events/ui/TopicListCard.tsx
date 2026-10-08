import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

interface TopicListCardProps {
  leading?: ReactNode;
  title: string;
  meta?: ReactNode;
  trailing?: ReactNode;
  className?: string;
}

/** Shared row anatomy for the Scheduled and Past topic lists: leading block, title + meta, trailing controls. */
export function TopicListCard({
  leading,
  title,
  meta,
  trailing,
  className,
}: Readonly<TopicListCardProps>) {
  return (
    <article
      className={cn(
        "bg-surface border-border flex items-center gap-3 rounded-[var(--radius-md)] border px-4 py-3 shadow-[var(--shadow-sm)] transition-shadow hover:shadow-[var(--shadow-md)]",
        className,
      )}
    >
      {leading}
      <div className="min-w-0 flex-1">
        <p className="text-foreground truncate text-sm leading-snug font-semibold">
          {title}
        </p>
        {meta && (
          <div className="mt-1 flex flex-wrap items-center gap-1.5">{meta}</div>
        )}
      </div>
      {trailing && (
        <div className="flex shrink-0 items-center gap-2">{trailing}</div>
      )}
    </article>
  );
}
