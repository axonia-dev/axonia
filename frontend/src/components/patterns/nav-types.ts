import type { ReactNode } from "react";

export interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
  /** Absolute paths whose pages belong to this item's section: they light it in
   *  the rail and name it as their breadcrumb parent. */
  owns?: readonly string[];
}

export type GroupChrome = "header" | "divider" | "none";

export interface NavGroup {
  label: string;
  /** Optional icon used by the mobile category tab (desktop uses a text label). */
  icon?: ReactNode;
  /** How the group announces itself: the uppercase label, a bare separator, or
   *  nothing at all for a lead group the surface title already covers. `label` is
   *  the nav's accessible name either way, drawn or not. */
  chrome?: GroupChrome;
  items: ReadonlyArray<NavItem>;
}
