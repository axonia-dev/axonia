import type { NavGroup, NavItem } from "@/components/patterns/nav-types";
import { cn } from "@/lib/cn";

/** Bottom-bar tab styling (mobile). */
export function tabClass({ isActive }: { isActive: boolean }) {
  return cn(
    "flex flex-1 flex-col items-center gap-1 rounded-md py-1 text-xs transition-colors",
    isActive ? "text-primary" : "text-muted-foreground",
  );
}

/** A link inside the mobile sheet/dialog. */
export function sheetLinkClass({ isActive }: { isActive: boolean }) {
  return cn(
    "flex items-center gap-3 rounded-md px-3 py-3 text-sm transition-colors",
    isActive
      ? "bg-surface-sunken text-primary"
      : "text-foreground data-[hovered]:bg-surface-sunken",
  );
}

export const groupHeaderClass =
  "text-muted-foreground px-3 text-xs font-semibold tracking-wide uppercase opacity-70";

/** True when `pathname` is `prefix` itself or a segment below it. A bare prefix
 *  test would light `/library` for `/library-types`. */
function isUnder(prefix: string, pathname: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

/** Active on exact match when `end`, else on the path or anything below it, and
 *  in either case on a path the item owns. Ownership is what keeps a section lit
 *  while one of its sub-pages is open, which no prefix rule can express. */
export function isPathActive(
  to: string,
  pathname: string,
  end: boolean,
  owns: readonly string[],
): boolean {
  const own = end ? pathname === to : isUnder(to, pathname);
  return own || owns.some((path) => isUnder(path, pathname));
}

export function isItemActive(item: NavItem, pathname: string): boolean {
  return isPathActive(item.to, pathname, item.end ?? false, item.owns ?? []);
}

/** A group's mobile tab is active when any of its items is active. */
export function isGroupActive(group: NavGroup, pathname: string) {
  return group.items.some((item) => isItemActive(item, pathname));
}

/** The item that owns `pathname`: the section a sub-page sits under. Only an
 *  owned match answers, so a section's own page never becomes its own parent. */
export function owningItem(
  groups: ReadonlyArray<NavGroup>,
  pathname: string,
): NavItem | undefined {
  for (const group of groups) {
    for (const item of group.items) {
      if ((item.owns ?? []).some((path) => isUnder(path, pathname)))
        return item;
    }
  }
  return undefined;
}
