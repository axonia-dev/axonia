import { Lock } from "lucide-react";

/** Marks a built-in (system) role as read-only and non-deletable. */
export function SystemBadge() {
  return (
    <span className="border-border bg-surface-sunken text-muted-foreground inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium">
      <Lock size={11} aria-hidden />
      System
    </span>
  );
}
