import type { CSSProperties } from "react";

import { areaHue } from "@/lib/area-hue";
import { cn } from "@/lib/cn";

export interface AreaTagProps {
  area: string;
  /** An area both people named: outlined and starred. */
  shared?: boolean;
  size?: "sm" | "md";
}

const SIZES = {
  sm: "h-5 px-2 text-[11px]",
  md: "h-6 px-2.5 text-xs",
} as const;

function tintVariables(area: string): CSSProperties {
  const { light, dark } = areaHue(area);
  return {
    "--area-bg": light.background,
    "--area-fg": light.foreground,
    "--area-edge": light.border,
    "--area-bg-dark": dark.background,
    "--area-fg-dark": dark.foreground,
    "--area-edge-dark": dark.border,
  } as CSSProperties;
}

/** A non-interactive tag in the area's own colour, the same colour wherever the area shows. */
export function AreaTag({
  area,
  shared = false,
  size = "sm",
}: Readonly<AreaTagProps>) {
  return (
    <span
      style={tintVariables(area)}
      {...(shared ? { role: "img", "aria-label": `${area}, shared` } : {})}
      className={cn(
        "inline-flex max-w-full min-w-0 flex-none items-center gap-1 rounded-full border font-semibold whitespace-nowrap",
        "border-transparent bg-(--area-bg) text-(--area-fg) in-[.theme-dark]:bg-(--area-bg-dark) in-[.theme-dark]:text-(--area-fg-dark)",
        shared &&
          "border-(--area-edge) in-[.theme-dark]:border-(--area-edge-dark)",
        SIZES[size],
      )}
    >
      <span className="truncate">{area}</span>
      {shared && <span aria-hidden>✦</span>}
    </span>
  );
}
