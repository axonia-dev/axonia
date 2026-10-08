import type { ReactNode } from "react";

import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/cn";

interface FilterToggleChipProps {
  active: boolean;
  onToggle: () => void;
  icon?: ReactNode;
  children: ReactNode;
}

/**
 * A pill-shaped toggle chip for a toolbar's second row (e.g. "To review", "Loved").
 * Active state uses the primary accent; inactive is a quiet outlined chip.
 */
export function FilterToggleChip({
  active,
  onToggle,
  icon,
  children,
}: Readonly<FilterToggleChipProps>) {
  return (
    <Button
      variant="ghost"
      size="sm"
      aria-pressed={active}
      onPress={onToggle}
      className={cn(
        "shrink-0 gap-1.5 rounded-full border px-3 py-1 font-medium",
        active
          ? "border-primary bg-primary text-primary-foreground hover:bg-primary-strong"
          : "border-border bg-surface text-muted-foreground hover:border-border-strong hover:bg-surface hover:text-foreground shadow-sm",
      )}
    >
      {icon}
      {children}
    </Button>
  );
}
