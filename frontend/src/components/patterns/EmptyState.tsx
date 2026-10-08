import type { ReactNode } from "react";

import { cn } from "@/lib/cn";

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
  icon?: ReactNode;
}

export function EmptyState({
  title,
  description,
  action,
  className,
  icon,
}: Readonly<EmptyStateProps>) {
  return (
    <div
      className={cn(
        "border-border bg-surface flex flex-col items-center gap-2 rounded-lg border border-dashed px-6 py-12 text-center",
        className,
      )}
    >
      {icon && (
        <span
          aria-hidden="true"
          className="text-muted-foreground/60 [&>svg]:h-8 [&>svg]:w-8"
        >
          {icon}
        </span>
      )}
      <p className="font-display text-foreground text-lg font-semibold">
        {title}
      </p>
      {description && (
        <p className="text-muted-foreground max-w-sm text-sm">{description}</p>
      )}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}
