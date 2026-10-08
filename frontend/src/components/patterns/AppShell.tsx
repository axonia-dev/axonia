import { type CSSProperties, type ReactNode, useEffect, useRef } from "react";
import { useLocation } from "react-router-dom";

import type { NavGroup, NavItem } from "@/components/patterns/nav-types";
import { type ShellTab, ShellTabBar } from "@/components/patterns/ShellTabBar";
import { useSidebar } from "@/components/patterns/SidebarProvider";
import {
  RailGroup,
  RailNav,
  RailToggleBadge,
  SideRail,
} from "@/components/patterns/SideRail";
import { AccountMenu } from "@/features/identity/ui/AccountMenu";
import { cn } from "@/lib/cn";

/** The shared application frame: collapsible desktop rail (switcher + primary nav +
 *  grouped nav), a desktop top bar pinning the account menu at its far right (with
 *  the caller's own content, if any, on the left), and a mobile top bar / content /
 *  bottom tab bar. The account menu lives in exactly this one place - the rail no
 *  longer carries it - so every shell shows it in the same spot rather than each
 *  assembling its own near-identical header. GlobalShell, CommunityShell and
 *  SettingsShell differ only in the data and slot content they pass in. */
export function AppShell({
  railTestId,
  renderSwitcher,
  primary,
  groups,
  mobileTopbar,
  desktopHeader,
  headerActions,
  tabs,
  tabsAriaLabel,
  tabsTestId,
  mainClassName,
  mainStyle,
  children,
}: Readonly<{
  railTestId: string;
  renderSwitcher: (collapsed: boolean) => ReactNode;
  primary: { label: string; items: ReadonlyArray<NavItem> };
  groups: ReadonlyArray<NavGroup>;
  mobileTopbar: ReactNode;
  /** Left-hand content of the desktop top bar (a breadcrumb, a title, or nothing);
   *  the account menu at the bar's far right is AppShell's own, not part of this slot. */
  desktopHeader: ReactNode;
  /** Controls pinned to the bar's right, immediately before the account menu: a shell states
   *  what it puts there, or `null`. Kept apart from `desktopHeader` because that slot is the
   *  left-hand one, and a right-hand control passed through it would only be right-aligned by
   *  accident. */
  headerActions: ReactNode;
  tabs: ReadonlyArray<ShellTab>;
  tabsAriaLabel: string;
  tabsTestId: string;
  mainClassName: string;
  /** A background painted on the full-width scroll container itself, rather than the
   *  narrower centred column inside it, so it reads as a wash across the page rather
   *  than a banded panel with visible edges. Optional: only CommunityShell uses it. */
  mainStyle?: CSSProperties;
  children: ReactNode;
}>) {
  const { collapsed } = useSidebar();
  // The shell fills the box the route guard gives it (the viewport, less whatever
  // frame the edition draws around a delegated tab): the rail and header are persistent chrome and only
  // <main> scrolls. Reset that scroll on navigation, since an internal scroll
  // container (unlike the document) does not do it.
  const { pathname } = useLocation();
  const mainRef = useRef<HTMLElement>(null);
  useEffect(() => {
    if (mainRef.current) mainRef.current.scrollTop = 0;
  }, [pathname]);
  return (
    <div className="bg-background text-foreground flex h-full flex-col overflow-hidden md:flex-row">
      <SideRail testId={railTestId}>
        <div className={cn("relative", collapsed ? "self-center" : "w-full")}>
          {renderSwitcher(collapsed)}
          <RailToggleBadge className="-top-1.5 -right-1.5" />
        </div>
        {primary.items.length > 0 && (
          <RailNav label={primary.label} items={primary.items} />
        )}
        {groups.map((group) => (
          <RailGroup key={group.label} group={group} collapsed={collapsed} />
        ))}
      </SideRail>

      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        {mobileTopbar}
        <header
          data-testid={railTestId.replace("-rail", "-desktop-topbar")}
          className="border-border hidden items-center gap-4 border-b px-6 py-3 md:flex"
        >
          <div className="min-w-0 flex-1">{desktopHeader}</div>
          {headerActions}
          <AccountMenu />
        </header>
        {/* `relative` so <main> is the containing block for the absolutely positioned
            elements inside it, react-aria's visually-hidden inputs above all. Without it
            theirs is the initial containing block, so <main>'s overflow does not clip them
            and each one below the fold extends the *document's* scroll area: a second
            scrollbar beside main's own, scrolling nothing but empty space. */}
        <main
          ref={mainRef}
          className="relative min-h-0 flex-1 overflow-y-auto"
          style={mainStyle}
        >
          <div className={mainClassName}>{children}</div>
        </main>
      </div>

      <ShellTabBar ariaLabel={tabsAriaLabel} testId={tabsTestId} tabs={tabs} />
    </div>
  );
}
