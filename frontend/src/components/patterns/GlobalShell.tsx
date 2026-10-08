import type { LucideIcon } from "lucide-react";
import {
  Bookmark,
  Lightbulb,
  Settings,
  UserRound,
  Users,
  Zap,
} from "lucide-react";
import { Outlet, useLocation } from "react-router-dom";

import { AppShell } from "@/components/patterns/AppShell";
import type { NavGroup, NavItem } from "@/components/patterns/nav-types";
import { ACCOUNT_SETTINGS } from "@/components/patterns/settings-nav";
import type { ShellTab } from "@/components/patterns/ShellTabBar";
import { OrganizationSwitcher } from "@/features/admin/ui/OrganizationSwitcher";
import { NotificationBell } from "@/features/communication/ui/NotificationBell";
import { useReportObservedZone } from "@/features/identity/api/use-time-zone";
import { AccountMenu } from "@/features/identity/ui/AccountMenu";
import {
  OnboardingAffordance,
  useOnboardingPath,
} from "@/features/onboarding/ui/OnboardingAffordance";
import { useVisitRecorder } from "@/features/onboarding/ui/use-visit-recorder";

// Builds a single NavItem, keeping the `end: true` + icon-size convention shared
// by every entry in the arrays below in one place instead of repeated per item.
function navItem(to: string, label: string, Icon: LucideIcon): NavItem {
  return {
    to,
    label,
    icon: <Icon size={20} />,
    end: true,
  };
}

// Communities is the one always-primary destination at the top.
const COMMUNITIES: NavItem = navItem("/communities", "Communities", Users);
// Settings opens Account settings directly; Organization settings (when the
// caller has any) and Documentation live behind the account menu instead. The
// settings route is registered as a sibling of GlobalShell, not nested under
// it (see app-routes.tsx), so this shell - and this tab bar - is never
// mounted on a `/settings/...` URL; there is no sub-page for this tab to stay
// lit on.
const SETTINGS: NavItem = navItem(`/${ACCOUNT_SETTINGS}`, "Settings", Settings);
const PRIMARY: ReadonlyArray<NavItem> = [COMMUNITIES];

// Personal participation - the rail's only group. Everything configurable now
// lives behind the account menu in one of the two settings surfaces.
const ACTIVITY_GROUP: NavGroup = {
  label: "Activity",
  items: [
    navItem("/me/impact", "My impact", Zap),
    navItem("/me/expertise", "My expertise", Lightbulb),
    navItem("/me/saved", "Saved", Bookmark),
  ],
};

function linkTab(item: NavItem): ShellTab {
  return {
    kind: "link",
    to: item.to,
    icon: item.icon,
    label: item.label,
    end: item.end ?? true,
  };
}

export function GlobalShell() {
  const location = useLocation();
  // Read once and record once here, even though OnboardingAffordance itself renders in both
  // the mobile and desktop header slots below (only one visible at a time via CSS): the read
  // dedupes on the shared query key regardless, but the recorder is a write, and mounting it
  // from the affordance would fire it twice per arrival.
  useVisitRecorder(useOnboardingPath());
  // Mounted here for the same reason, and it is the same trap: this is a write, and the header
  // slots below render twice.
  useReportObservedZone();

  // The rail holds only places you go, never things you configure: one group.
  const railGroups: ReadonlyArray<NavGroup> = [ACTIVITY_GROUP];

  const meTab: ShellTab = {
    kind: "menu",
    key: "me",
    icon: <UserRound size={20} />,
    label: "Me",
    isActive: location.pathname.startsWith("/me/"),
    sheetTitle: "Me",
    // Personal participation only - not "whatever the rail holds" (identical today
    // because the rail has exactly one group, but a future non-personal rail group
    // must not silently appear inside a sheet titled "Me").
    groups: [ACTIVITY_GROUP],
  };

  // Bar order: Communities · Me · Settings.
  const tabs: ShellTab[] = [linkTab(COMMUNITIES), meTab, linkTab(SETTINGS)];

  const mainClassName =
    "mx-auto w-full max-w-3xl flex-1 px-6 pt-8 pb-24 md:pb-8";

  return (
    <AppShell
      railTestId="global-rail"
      renderSwitcher={(collapsed) => (
        <OrganizationSwitcher variant={collapsed ? "compact" : "full"} />
      )}
      primary={{ label: "Primary", items: PRIMARY }}
      groups={railGroups}
      mobileTopbar={
        <header
          data-testid="global-topbar"
          className="border-border flex items-center gap-2 border-b px-4 py-3 md:hidden"
        >
          <div className="min-w-0 flex-1">
            <OrganizationSwitcher variant="full" />
          </div>
          <OnboardingAffordance />
          <NotificationBell />
          <AccountMenu />
        </header>
      }
      desktopHeader={null}
      headerActions={
        <>
          <OnboardingAffordance />
          <NotificationBell />
        </>
      }
      tabs={tabs}
      tabsAriaLabel="Primary"
      tabsTestId="global-tabs"
      mainClassName={mainClassName}
    >
      <Outlet />
    </AppShell>
  );
}
