import { ChevronLeft, ChevronRight } from "lucide-react";
import type { ReactNode } from "react";

import type { GroupChrome, NavGroup } from "@/components/patterns/nav-types";
import { RailLink } from "@/components/patterns/RailLink";
import { useSidebar } from "@/components/patterns/SidebarProvider";
import { cn } from "@/lib/cn";

export function RailToggleBadge({
  className,
}: Readonly<{ className?: string }>) {
  const { collapsed, toggle } = useSidebar();
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
      aria-expanded={!collapsed}
      className={cn(
        "border-border bg-surface text-muted-foreground hover:text-primary hover:border-primary focus-visible:ring-focus-ring absolute z-10 flex size-4 cursor-pointer items-center justify-center rounded-full border shadow-sm transition-colors focus-visible:ring-2 focus-visible:outline-none",
        className,
      )}
    >
      {collapsed ? (
        <ChevronRight size={11} strokeWidth={2.5} />
      ) : (
        <ChevronLeft size={11} strokeWidth={2.5} />
      )}
    </button>
  );
}

interface SideRailProps {
  testId: string;
  children: ReactNode;
}

export function SideRail({ testId, children }: Readonly<SideRailProps>) {
  const { collapsed } = useSidebar();
  return (
    <aside
      data-testid={testId}
      className={cn(
        "border-border bg-surface-sunken relative hidden min-h-0 shrink-0 flex-col gap-6 overflow-y-auto border-r py-5 md:flex",
        "motion-safe:transition-[width] motion-safe:duration-200 motion-safe:ease-in-out",
        collapsed ? "w-[68px] items-center px-2" : "w-60 px-3",
      )}
    >
      {children}
    </aside>
  );
}

export interface RailNavItem {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
  owns?: readonly string[];
}

/** A labelled group of rail destinations, each collapse-aware via RailLink. */
export function RailNav({
  label,
  items,
}: Readonly<{ label: string; items: ReadonlyArray<RailNavItem> }>) {
  return (
    <nav aria-label={label} className="flex flex-col gap-1">
      {items.map((item) => (
        <RailLink
          key={item.to}
          to={item.to}
          label={item.label}
          icon={item.icon}
          end={item.end ?? false}
          owns={item.owns ?? []}
        />
      ))}
    </nav>
  );
}

/** The mark above a group: its uppercase label, or a bare separator when the rail is
 *  collapsed (no room for text) or the group asked for one. */
function GroupMark({
  chrome,
  label,
  collapsed,
}: Readonly<{ chrome: GroupChrome; label: string; collapsed: boolean }>) {
  if (collapsed || chrome === "divider") {
    return (
      <div
        className={cn(
          "bg-border my-1 h-px",
          collapsed ? "w-7 self-center" : "mx-3",
        )}
        aria-hidden
      />
    );
  }
  return (
    <div className="mt-2 mb-0.5 flex items-center gap-2 px-3">
      <p className="text-muted-foreground text-[0.625rem] font-semibold tracking-[0.14em] uppercase opacity-70">
        {label}
      </p>
      <span aria-hidden className="bg-border h-px flex-1" />
    </div>
  );
}

/** A rail-rendered group: its mark (see {@link GroupMark}) + its links. A `"none"`
 *  group draws neither, for a lead group whose meaning the surface title already
 *  carries. */
export function RailGroup({
  group,
  collapsed,
}: Readonly<{ group: NavGroup; collapsed: boolean }>) {
  const chrome = group.chrome ?? "header";
  return (
    <div className="flex flex-col gap-1">
      {chrome !== "none" && (
        <GroupMark chrome={chrome} label={group.label} collapsed={collapsed} />
      )}
      <RailNav label={group.label} items={group.items} />
    </div>
  );
}
