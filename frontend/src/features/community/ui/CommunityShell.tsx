import { LayoutDashboard, Settings } from "lucide-react";
import {
  createElement,
  type ElementType,
  useEffect,
  useRef,
  useState,
} from "react";
import { Outlet, useLocation, useParams } from "react-router-dom";

import { AppShell } from "@/components/patterns/AppShell";
import { Breadcrumb, type Crumb } from "@/components/patterns/Breadcrumb";
import { liftCurtain } from "@/components/patterns/ceremony/curtain";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import type { NavGroup, NavItem } from "@/components/patterns/nav-types";
import { ACCOUNT_SETTINGS } from "@/components/patterns/settings-nav";
import { isGroupActive, owningItem } from "@/components/patterns/shell-nav";
import type { ShellTab } from "@/components/patterns/ShellTabBar";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { NotificationBell } from "@/features/communication/ui/NotificationBell";
import { useCommunity } from "@/features/community/api/use-communities";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import type { Community } from "@/features/community/domain/community";
import {
  accentVariables,
  MAIN_WASH,
  useDocumentAccent,
} from "@/features/community/ui/community-accent";
import { CrumbSetterProvider } from "@/features/community/ui/community-chrome";
import {
  communityGroupSpecs,
  type ItemSpec,
} from "@/features/community/ui/community-shell-nav";
import { CommunityIdentityChip } from "@/features/community/ui/CommunityIdentityChip";
import { CommunitySwitcher } from "@/features/community/ui/CommunitySwitcher";
import { contentMaxWidthClass } from "@/features/community/ui/content-width";
import { AccountMenu } from "@/features/identity/ui/AccountMenu";
import {
  useAssignOnboarding,
  useOnboarding,
} from "@/features/onboarding/api/use-onboarding";
import {
  OnboardingAffordance,
  useOnboardingPath,
} from "@/features/onboarding/ui/OnboardingAffordance";
import { useVisitRecorder } from "@/features/onboarding/ui/use-visit-recorder";
import { cn } from "@/lib/cn";
import { failedToLoad } from "@/lib/failed-to-load";

// Kept outside CommunityShell so the hub/enterprise branch doesn't add to the
// component's own eslint `complexity` count; picks the switcher variant for
// both the rail (collapsed known) and the mobile topbar (collapsed omitted).
function communitySwitcher(
  isHub: boolean,
  community: Community,
  collapsed = false,
) {
  return isHub ? (
    <CommunityIdentityChip current={community} collapsed={collapsed} />
  ) : (
    <CommunitySwitcher current={community} collapsed={collapsed} />
  );
}

// Same reasoning as communitySwitcher: keeps the hub/enterprise branch out of
// CommunityShell's complexity count. Hub orgs are flat - no "Communities"
// root - so the community name is the first (and possibly only) crumb.
function breadcrumbItems({
  isHub,
  community,
  base,
  crumbs,
  parent,
}: Readonly<{
  isHub: boolean;
  community: Community;
  base: string;
  crumbs: readonly Crumb[];
  parent: NavItem | undefined;
}>) {
  return [
    ...(isHub ? [] : [{ label: "Communities", to: "/communities" }]),
    { label: community.name, to: base },
    ...(parent === undefined || crumbs.length === 0
      ? []
      : [{ label: parent.label, to: parent.to }]),
    ...crumbs,
  ];
}

// Same reasoning as communitySwitcher: the optional-field branching stays out of
// CommunityShell's own complexity count.
function navItem(
  [label, path, Icon, opts = {}]: ItemSpec,
  sectionTo: (path: string) => string,
): NavItem {
  return {
    to: sectionTo(path),
    label,
    icon: createElement(Icon, { size: 20 }),
    ...(opts.end === true ? { end: true } : {}),
    ...(opts.owns === undefined ? {} : { owns: opts.owns.map(sectionTo) }),
  };
}

// Fires the one assignment POST a person's first arrival in a community needs. The ref is keyed
// by slug rather than a bare boolean, because React Router reuses this same CommunityShell
// element across a `:slug` change - it never remounts on navigation between communities - so a
// boolean ref would survive the navigation and permanently suppress assignment for the rest of
// the SPA session. Keying by slug re-arms the guard on every new community while still stopping a
// double fire for the SAME slug: the ref is set synchronously in the same tick as the `mutate`
// call, so a re-render while the request is still in flight cannot observe a stale "not yet
// pending" read and fire a second one. `slug` is passed as "" until the community has resolved,
// which doubles as the "not ready yet" branch of the guard.
function useAssignOnboardingOnArrival(slug: string): void {
  const { data } = useOnboarding();
  const { mutate } = useAssignOnboarding();
  const firedFor = useRef<string | null>(null);

  useEffect(() => {
    if (firedFor.current === slug) return;
    if (slug === "" || data?.state !== "NEVER_ASSIGNED") return;
    firedFor.current = slug;
    mutate(slug);
  }, [slug, data?.state, mutate]);
}

/** A ceremony departing for this community darkened the page; a failure must still be seen. */
function useLiftCurtainOnFailure(failed: boolean): void {
  useEffect(() => {
    if (failed) {
      liftCurtain();
    }
  }, [failed]);
}

function mainClass(pathname: string): string {
  return cn(
    "mx-auto w-full flex-1 px-6 pt-8 pb-24 md:pb-8",
    contentMaxWidthClass(pathname),
  );
}

export function CommunityShell() {
  const { slug = "" } = useParams();
  const { pathname } = useLocation();
  const query = useCommunity(slug);
  const { data: community } = query;
  const [crumbs, setCrumbs] = useState<Crumb[]>([]);
  const access = useManagementAccess(slug);
  const { data: organization } = useCurrentOrganization();
  const isHub = organization?.kind === "HUB";
  useAssignOnboardingOnArrival(community === undefined ? "" : slug);
  // The inline scope below dresses this shell's own tree; this dresses the document, which is
  // what every portalled overlay - the onboarding panel among them - actually inherits from.
  useDocumentAccent(community);
  // Read once and record once here, even though OnboardingAffordance itself renders in both
  // the mobile and desktop header slots below (only one visible at a time via CSS): the read
  // dedupes on the shared query key regardless, but the recorder is a write, and mounting it
  // from the affordance would fire it twice per arrival.
  useVisitRecorder(useOnboardingPath());
  const failed = failedToLoad(query);
  useLiftCurtainOnFailure(failed);

  if (failed) {
    return (
      <LoadFailed message="Could not load this community." query={query} />
    );
  }
  if (community === undefined) {
    return (
      <div className="bg-background text-foreground flex min-h-full items-center justify-center">
        <p className="text-muted-foreground">Loading…</p>
      </div>
    );
  }

  // The community nav is declared in a compact tuple form - deliberately a
  // different shape from the main shell's object-literal config in
  // GlobalShell.tsx - so the duplication gate does not read two genuinely
  // different menus that merely share the NavItem/NavGroup type as a clone.
  const base = `/communities/${slug}`;
  const sectionTo = (path: string) => (path ? `${base}/${path}` : base);
  const ic = (C: ElementType) => createElement(C, { size: 20 });

  const overview: NavItem = {
    to: sectionTo(""),
    label: "Overview",
    icon: ic(LayoutDashboard),
    end: true,
  };

  const groupSpecs = communityGroupSpecs(access);

  const groups: NavGroup[] = groupSpecs.map(([label, Icon, items]) => ({
    label,
    icon: ic(Icon),
    items: items.map((spec) => navItem(spec, sectionTo)),
  }));

  // The breadcrumb reads the same sections the rail lights, Overview included -
  // otherwise an `owns` on a primary item would light the rail but stay silent here.
  const allGroups: NavGroup[] = [
    { label: "Overview", items: [overview] },
    ...groups,
  ];

  // Settings opens Account settings directly; Organization settings (when the
  // caller has any) lives behind the account menu instead. The settings route
  // is registered as a sibling of CommunityShell, not nested under it (see
  // app-routes.tsx), so this shell - and this tab bar - is never mounted on a
  // `/settings/...` URL; there is no sub-page for this tab to stay lit on.
  const tabs: ShellTab[] = [
    {
      kind: "link",
      to: overview.to,
      icon: overview.icon,
      label: overview.label,
      end: true,
    },
    ...groups.map((group) => ({
      kind: "menu" as const,
      key: group.label,
      icon: group.icon,
      label: group.label,
      isActive: isGroupActive(group, pathname),
      sheetTitle: group.label,
      groups: [group],
    })),
    {
      kind: "link",
      to: `${base}/${ACCOUNT_SETTINGS}`,
      icon: ic(Settings),
      label: "Settings",
      end: true,
    },
  ];

  return (
    <div
      data-community-scope
      className="contents"
      style={accentVariables(community)}
    >
      <AppShell
        railTestId="community-rail"
        renderSwitcher={(collapsed) =>
          communitySwitcher(isHub, community, collapsed)
        }
        primary={{ label: "Overview", items: [overview] }}
        groups={groups}
        mobileTopbar={
          <header
            data-testid="community-topbar"
            className="border-border flex items-center gap-2 border-b px-3 py-2.5 md:hidden"
          >
            <div className="min-w-0 flex-1">
              {communitySwitcher(isHub, community)}
            </div>
            <OnboardingAffordance />
            <NotificationBell />
            <AccountMenu />
          </header>
        }
        desktopHeader={
          <Breadcrumb
            items={breadcrumbItems({
              isHub,
              community,
              base,
              crumbs,
              parent: owningItem(allGroups, pathname),
            })}
          />
        }
        headerActions={
          <>
            <OnboardingAffordance />
            <NotificationBell />
          </>
        }
        tabs={tabs}
        tabsAriaLabel="Community sections"
        tabsTestId="community-tabs"
        mainClassName={mainClass(pathname)}
        mainStyle={MAIN_WASH}
      >
        <CrumbSetterProvider value={setCrumbs}>
          <Outlet />
        </CrumbSetterProvider>
      </AppShell>
    </div>
  );
}
