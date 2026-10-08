import type { ReactNode } from "react";
import { Focusable, TooltipTrigger } from "react-aria-components";
import { Link, useLocation } from "react-router-dom";

import { isPathActive } from "@/components/patterns/shell-nav";
import { useSidebar } from "@/components/patterns/SidebarProvider";
import { Tooltip } from "@/components/ui/Tooltip";
import { cn } from "@/lib/cn";
import { useMediaQuery } from "@/lib/use-media-query";

interface RailLinkProps {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
  owns?: readonly string[];
}

function railLinkClass(isActive: boolean, collapsed: boolean): string {
  return cn(
    "relative flex items-center rounded-md text-sm transition-colors",
    collapsed ? "h-10 w-10 justify-center" : "gap-3 px-3 py-2",
    isActive
      ? "text-primary bg-[color-mix(in_srgb,var(--primary)_11%,var(--surface))] font-semibold"
      : "text-muted-foreground hover:text-foreground hover:bg-[color-mix(in_srgb,var(--primary)_6%,var(--surface))]",
    // Primary accent bar: the on-brand marker of "you are here". An inset shadow rather than a
    // positioned span, so the bar runs the item's whole height and the border radius clips its
    // ends into the corner instead of leaving a square edge outside the rounded background.
    // Hidden while collapsed, where the item is a square icon with no left edge to mark.
    isActive && !collapsed && "shadow-[inset_2px_0_0_var(--primary)]",
  );
}

export function RailLink({
  to,
  label,
  icon,
  end = false,
  owns = [],
}: Readonly<RailLinkProps>) {
  const { collapsed } = useSidebar();
  const isDesktop = useMediaQuery("(min-width: 48rem)");
  const { pathname } = useLocation();
  // Not NavLink: react-router gates any aria-current value on its own match
  // (aria-current = isActive ? ariaCurrentProp : undefined), so an owned path
  // could be painted active but never announced.
  const isActive = isPathActive(to, pathname, end, owns);
  const link = (
    <Link
      to={to}
      aria-current={isActive ? "page" : undefined}
      className={railLinkClass(isActive, collapsed)}
    >
      <span className="relative flex shrink-0">{icon}</span>
      <span className={cn(collapsed && "sr-only")}>{label}</span>
    </Link>
  );

  // Collapsed-only: the label moves from inline text to a hover/focus tooltip.
  // `Focusable` bridges react-aria's hover/focus handling onto `Link`, a plain
  // (non react-aria) component, by cloning the merged props onto its single child.
  // The tooltip portals to `document.body`, so unlike the old absolutely
  // positioned span it never becomes part of the rail's scrollable content.
  // Below md the rail is hidden whatever its collapsed state, so no trigger mounts there.
  if (!collapsed || !isDesktop) return link;

  return (
    <TooltipTrigger delay={300}>
      <Focusable>{link}</Focusable>
      <Tooltip placement="right">{label}</Tooltip>
    </TooltipTrigger>
  );
}
