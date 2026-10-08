import type { IconName } from "lucide-react/dynamic";
import { DynamicIcon } from "lucide-react/dynamic";
import type { CSSProperties } from "react";

import { cn } from "@/lib/cn";

interface AccentIconProps {
  name: IconName;
  /** Any CSS colour — e.g. "var(--primary)" or "#de3b78". */
  accent: string;
  size?: number;
  className?: string;
}

/** A Lucide glyph on its accent tint — the shared building block for section
 *  chips and stat icons (generalises TypeIcon's recipe). */
export function AccentIcon({
  name,
  accent,
  size = 18,
  className,
}: Readonly<AccentIconProps>) {
  return (
    <span
      aria-hidden
      style={{ "--k": accent } as CSSProperties}
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-md",
        "bg-[color-mix(in_srgb,var(--k)_16%,var(--surface))] text-[color-mix(in_srgb,var(--k)_80%,var(--foreground))]",
        className,
      )}
    >
      <DynamicIcon name={name} size={size} strokeWidth={1.8} />
    </span>
  );
}
