import type { CSSProperties } from "react";
import { useId } from "react";

import { cn } from "@/lib/cn";

interface LogoProps {
  className?: string;
  size?: number;
  /**
   * Tile tint colour. Defaults to the brand `--primary`; the organization
   * switcher passes the active org's hue so the mark "wears" its workspace,
   * washing between hues (transitioned) as you switch.
   */
  tint?: string;
}

/**
 * The Axonia brand chip: the axon "A" firing into a spark, drawn from the theme
 * tokens (secondary → primary) so it adapts to light/dark. Seated in a subtle
 * primary-tinted rounded square built like the community Avatar (tinted bg +
 * mark), so the global and community rails read as siblings — the brand is just
 * "tinted by --primary" where a community is tinted by its kind hue.
 *
 * The mark is cropped tight (viewBox framed to its bounding box) so it fills the
 * chip and stays recognizable at rail size, unlike the tab-padded favicon.svg.
 */
export function Logo({ className, size = 36, tint }: Readonly<LogoProps>) {
  // Unique per instance: the rail, mobile topbar and switcher menus can all
  // render Logo at once, so a shared id would make every fill resolve to the
  // first (hidden) gradient and the mark's legs would vanish on the visible one.
  const gradientId = useId();
  return (
    <span
      style={
        { width: size, height: size, "--logo-tint": tint } as CSSProperties
      }
      className={cn(
        "inline-flex shrink-0 items-center justify-center rounded-md",
        "bg-[color-mix(in_srgb,var(--logo-tint,var(--primary))_14%,var(--surface))]",
        "motion-safe:transition-[background-color] motion-safe:duration-300",
        className,
      )}
    >
      <svg
        role="img"
        aria-label="Axonia"
        width="72%"
        height="72%"
        viewBox="25 31 134 134"
        fill="none"
      >
        <defs>
          <linearGradient id={gradientId} x1="0" y1="1" x2="1" y2="0">
            <stop offset="0" stopColor="var(--secondary)" />
            <stop offset="1" stopColor="var(--primary)" />
          </linearGradient>
        </defs>
        <path
          d="M88 44 Q69 98 50 152 L54.5 152 Q72.5 99 91 45.5 Z"
          fill={`url(#${gradientId})`}
        />
        <path
          d="M90 45.5 Q109 99 127 152 L131.5 152 Q113 97 92 44 Z"
          fill={`url(#${gradientId})`}
        />
        <line
          x1="67"
          y1="112"
          x2="112"
          y2="116"
          stroke="var(--primary)"
          strokeWidth="4.5"
          strokeLinecap="round"
          opacity="0.7"
        />
        <circle cx="98" cy="114" r="3" fill="var(--primary)" opacity="0.6" />
        <circle cx="112" cy="116" r="8" fill="var(--primary)" />
      </svg>
    </span>
  );
}
