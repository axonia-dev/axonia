import type { CSSProperties } from "react";

import { cn } from "@/lib/cn";
import { kindHue } from "@/lib/kind-hue";

interface KindBadgeProps {
  kindId: number;
  label: string;
  accentColor?: string | null;
  className?: string;
}

export function KindBadge({
  kindId,
  label,
  accentColor,
  className,
}: Readonly<KindBadgeProps>) {
  return (
    <span
      style={{ "--k": accentColor ?? kindHue(String(kindId)) } as CSSProperties}
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold whitespace-nowrap",
        "bg-[color-mix(in_srgb,var(--k)_15%,var(--surface))] text-[color-mix(in_srgb,var(--k)_80%,var(--foreground))]",
        className,
      )}
    >
      {label}
    </span>
  );
}
