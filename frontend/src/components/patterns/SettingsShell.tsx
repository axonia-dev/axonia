import { ArrowLeft, Settings, Users } from "lucide-react";
import { Focusable, TooltipTrigger } from "react-aria-components";
import { Link, Outlet } from "react-router-dom";

import { useIsOrgless } from "@/auth/use-is-orgless";
import { AppShell } from "@/components/patterns/AppShell";
import type { SettingsGroup } from "@/components/patterns/settings-nav";
import { toNavGroups } from "@/components/patterns/settings-nav";
import {
  BACK_LABEL,
  useAppReturnPath,
} from "@/components/patterns/settings-return";
import type { ShellTab } from "@/components/patterns/ShellTabBar";
import { Tooltip } from "@/components/ui/Tooltip";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { OrganizationSwitcher } from "@/features/admin/ui/OrganizationSwitcher";
import { NotificationBell } from "@/features/communication/ui/NotificationBell";
import { useCommunities } from "@/features/community/api/use-communities";
import { CommunityIdentityChip } from "@/features/community/ui/CommunityIdentityChip";
import { AccountMenu } from "@/features/identity/ui/AccountMenu";
import {
  OnboardingAffordance,
  useOnboardingPath,
} from "@/features/onboarding/ui/OnboardingAffordance";
import { useVisitRecorder } from "@/features/onboarding/ui/use-visit-recorder";
import { failedToLoad } from "@/lib/failed-to-load";
import { useMediaQuery } from "@/lib/use-media-query";

interface SettingsShellProps {
  /** Absolute prefix of this surface, e.g. `/settings/organization`. */
  base: string;
  title: string;
  /** Where leaving settings lands when there is nothing to go back to. Also the bottom
   *  tab bar's back tab, which stays a fixed destination: a tab names a place, while the
   *  arrow above means "back". */
  backTo: string;
  /** Short name of `backTo`, for that tab. The caller states it per surface (e.g.
   *  "Communities" at the top level) rather than SettingsShell guessing from the path. A
   *  hub session passes the generic "Community" rather than fetching the community's own
   *  name here: that would add a network dependency to route wiring purely for a label. */
  backTabLabel: string;
  groups: ReadonlyArray<SettingsGroup>;
}

/** A hub's tenant identity, resolved without needing a community route param: a hub
 *  organization holds exactly one community, and the community list is already scoped to
 *  the caller's own organization (the same RLS boundary every other community read goes
 *  through), so it always resolves to that one row - whether settings was entered from the
 *  top level or from underneath the community itself. */
function HubTenantIdentity({ collapsed }: Readonly<{ collapsed: boolean }>) {
  const communities = useCommunities({ page: 0, size: 1 });
  // The shell gate already holds the hub's organization, the same tenant under the name it
  // carries there, so it is named rather than nothing; nothing community-only is guessed.
  if (failedToLoad(communities)) {
    return <OrganizationSwitcher variant={collapsed ? "compact" : "full"} />;
  }
  const current = communities.data?.content[0];
  if (current === undefined) return null;
  return <CommunityIdentityChip current={current} collapsed={collapsed} />;
}

/** The rail's identity chip while in settings: the same OrganizationSwitcher /
 *  CommunityIdentityChip every other shell shows at the rail top, picked by the same kind
 *  rule CommunityShell uses to choose between them. Identity only, never a fourth switcher. */
function TenantIdentity({ collapsed }: Readonly<{ collapsed: boolean }>) {
  const { data: organization } = useCurrentOrganization();
  if (organization === undefined) return null;
  if (organization.kind === "HUB") {
    return <HubTenantIdentity collapsed={collapsed} />;
  }
  return <OrganizationSwitcher variant={collapsed ? "compact" : "full"} />;
}

/**
 * The way out of settings, and the name of the mode you are in. Deliberately not a
 * breadcrumb: a trail asserts containment, and settings is contained by nothing -
 * `/settings/organization/users` is a sibling of the app's own routes, reached from the
 * account menu rather than by drilling down, so any first crumb would invent a parent.
 *
 * It is also all the top bar needs to say. The lit rail item already names the section
 * and the page's own heading names the page, so a trail here would be the third element
 * on screen saying "Organization".
 *
 * Rendered identically in the desktop and mobile bars: leaving a mode should not work
 * differently at different widths. Only the tooltip differs: it rides on the copy in the bar
 * this width shows, since a trigger inside the hidden bar can never be hovered or focused.
 */
function SettingsHeading({
  backTo,
  title,
  withTooltip,
}: Readonly<{ backTo: string; title: string; withTooltip: boolean }>) {
  // Genuinely back: whatever the member was doing when they entered settings, not a
  // fixed list. `backTo` is the fallback for a session with nothing to go back to - a
  // bookmark, a reload, or one of the legacy /admin/* paths opened cold.
  const to = useAppReturnPath(backTo);
  const label = BACK_LABEL;
  // Negative margin keeps the arrow optically flush while the padding holds the hit
  // area at 30px, over WCAG 2.2 AA's 24px minimum.
  const back = (
    <Link
      to={to}
      aria-label={label}
      className="text-muted-foreground hover:text-foreground hover:bg-surface focus-visible:ring-focus-ring -m-1.5 flex items-center rounded-md p-1.5 transition-colors outline-none focus-visible:ring-2"
    >
      <ArrowLeft size={18} aria-hidden />
    </Link>
  );
  return (
    <div className="flex min-w-0 flex-1 items-center gap-2">
      {withTooltip ? (
        <TooltipTrigger delay={300}>
          <Focusable>{back}</Focusable>
          {/* The arrow shows no destination, so the tooltip names it - the same treatment
              every collapsed rail icon gets. */}
          <Tooltip placement="bottom">{label}</Tooltip>
        </TooltipTrigger>
      ) : (
        back
      )}
      <span className="text-foreground min-w-0 truncate text-sm font-semibold">
        {title}
      </span>
    </div>
  );
}

/**
 * Entering settings swaps the app rail for the settings sections rather than adding a
 * second navigation column beside it. Below `md` there is no rail at all, so the swap
 * costs nothing there and the sections are reached through SettingsOverview instead.
 *
 * Composed out of AppShell rather than replacing it: the desktop top bar's account menu
 * and the tab bar are both wanted here unchanged, `renderSwitcher` holds the same tenant
 * identity chip every other shell puts above the nav, and both top bars carry the one
 * {@link SettingsHeading} - the way out, and the mode it leaves.
 */
export function SettingsShell({
  base,
  title,
  backTo,
  backTabLabel,
  groups,
}: Readonly<SettingsShellProps>) {
  // Read once and record once here, even though OnboardingAffordance itself renders in both
  // the mobile and desktop header slots below (only one visible at a time via CSS): the read
  // dedupes on the shared query key regardless, but the recorder is a write, and mounting it
  // from the affordance would fire it twice per arrival.
  useVisitRecorder(useOnboardingPath());
  // Notifications are held per organization; the Discover lobby shows no bell either.
  const bell = useIsOrgless() ? null : <NotificationBell />;

  const navGroups = toNavGroups(groups, base);
  const isDesktop = useMediaQuery("(min-width: 48rem)");
  const tabs: ShellTab[] = [
    {
      kind: "link",
      to: backTo,
      icon: <Users size={20} />,
      label: backTabLabel,
      // Exact match: `backTo` is an ancestor of every settings URL, so without
      // `end` this tab would prefix-match and stay lit across all of settings.
      end: true,
    },
    {
      kind: "link",
      to: base,
      icon: <Settings size={20} />,
      label: "Settings",
      // Prefix match: lights up on the overview and on every section beneath it.
      end: false,
    },
  ];

  return (
    <AppShell
      railTestId="settings-rail"
      renderSwitcher={(collapsed) => <TenantIdentity collapsed={collapsed} />}
      primary={{ label: "Settings", items: [] }}
      groups={navGroups}
      mobileTopbar={
        <header
          data-testid="settings-topbar"
          className="border-border flex items-center gap-2 border-b px-4 py-3 md:hidden"
        >
          <SettingsHeading
            backTo={backTo}
            title={title}
            withTooltip={!isDesktop}
          />
          <OnboardingAffordance />
          {bell}
          <AccountMenu />
        </header>
      }
      desktopHeader={
        <SettingsHeading
          backTo={backTo}
          title={title}
          withTooltip={isDesktop}
        />
      }
      headerActions={
        <>
          <OnboardingAffordance />
          {bell}
        </>
      }
      tabs={tabs}
      tabsAriaLabel="Settings"
      tabsTestId="settings-tabs"
      mainClassName="mx-auto w-full max-w-4xl flex-1 px-6 pt-8 pb-24 md:pb-8"
    >
      <Outlet />
    </AppShell>
  );
}
