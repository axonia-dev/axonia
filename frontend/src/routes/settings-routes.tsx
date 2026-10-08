import type { ReactNode } from "react";
import { Navigate, Route } from "react-router-dom";

import { useIsOrgless } from "@/auth/use-is-orgless";
import {
  ACCOUNT_SETTINGS,
  GROUP_ACCESS_PATH,
  ORGANIZATION_SETTINGS,
  organizationSettingsTitle,
  tenantWord,
} from "@/components/patterns/settings-nav";
import { SettingsLanding } from "@/components/patterns/SettingsLanding";
import { SettingsShell } from "@/components/patterns/SettingsShell";
import type { EditionSlots } from "@/edition";
import { RolesPage } from "@/features/admin/ui/RolesPage";
import { VOCABULARY_PATH } from "@/features/admin/ui/vocabulary";
import { VocabularyPage } from "@/features/admin/ui/VocabularyPage";
import { failedToLoad } from "@/lib/failed-to-load";
import { OrganizationEntry } from "@/routes/organization-entry";
import { OrganizationLoadFailed } from "@/routes/organization-load-failed";
import {
  AccountApiKeysRoute,
  AccountOrganizationRoute,
  AccountPrivacyRoute,
} from "@/routes/settings-routes.account-gates";
import { AccountGroups } from "@/routes/settings-routes.account-groups";
import {
  ACCOUNT_LEAF_PATHS,
  ACCOUNT_PAGES,
  API_KEYS_PATH,
  MOVED,
  ORGANIZATION_LEAF_PATHS,
  ORGANIZATION_SECTIONS,
  PRIVACY_PATH,
  ROLES_PATH,
} from "@/routes/settings-routes.catalogue";
import {
  accountPageRoutes,
  contributedSectionRoutes,
} from "@/routes/settings-routes.contributed";
import {
  useBackNav,
  useOrganizationSettingsGroups,
  useResolvedBase,
  useWithheldSection,
} from "@/routes/settings-routes.hooks";
import { OrganizationGroupAccessRoute } from "@/routes/settings-routes.organization-gates";

export { ACCOUNT_LEAF_PATHS, MOVED, ORGANIZATION_LEAF_PATHS };

type SettingsEdition = Pick<EditionSlots, "settingsSections" | "accountPages">;

function MovedRedirect({ to }: Readonly<{ to: string }>) {
  const resolved = useResolvedBase(to);
  return <Navigate to={resolved} replace />;
}

function movedRoutes(base: string, edition: SettingsEdition): ReactNode {
  return [
    ...MOVED,
    ...edition.settingsSections.flatMap((section) => section.moved ?? []),
    ...edition.accountPages.flatMap((page) => page.moved),
  ].map(([from, to]) => (
    <Route
      key={from}
      path={`${base}/${from}`}
      element={<MovedRedirect to={`${base}/${to}`} />}
    />
  ));
}

function AccountSettingsShell({ base }: Readonly<{ base: string }>) {
  const resolvedBase = useResolvedBase(base);
  const { backTo, backTabLabel } = useBackNav(base);
  return (
    <AccountGroups>
      {(groups) => (
        <SettingsShell
          base={`${resolvedBase}/${ACCOUNT_SETTINGS}`}
          title="Account settings"
          backTo={backTo}
          backTabLabel={backTabLabel}
          groups={groups}
        />
      )}
    </AccountGroups>
  );
}

function AccountSettingsOverview({ base }: Readonly<{ base: string }>) {
  const resolvedBase = useResolvedBase(base);
  return (
    <AccountGroups>
      {(groups) => (
        <SettingsLanding
          title="Account"
          base={`${resolvedBase}/${ACCOUNT_SETTINGS}`}
          groups={groups}
        />
      )}
    </AccountGroups>
  );
}

function accountSettingsRoutes(
  base: string,
  pages: EditionSlots["accountPages"],
): ReactNode {
  const accountBase = `${base}/${ACCOUNT_SETTINGS}`;
  return (
    <Route element={<AccountSettingsShell base={base} />}>
      <Route
        path={accountBase}
        element={<AccountSettingsOverview base={base} />}
      />
      {Object.entries(ACCOUNT_PAGES).map(([path, Page]) => (
        <Route
          key={path}
          path={`${accountBase}/${path}`}
          element={
            <AccountOrganizationRoute base={base}>
              <Page />
            </AccountOrganizationRoute>
          }
        />
      ))}
      <Route
        path={`${accountBase}/${PRIVACY_PATH}`}
        element={<AccountPrivacyRoute base={base} />}
      />
      <Route
        path={`${accountBase}/${API_KEYS_PATH}`}
        element={<AccountApiKeysRoute base={base} />}
      />
      {accountPageRoutes(base, pages)}
    </Route>
  );
}

function OrganizationSettingsShell({ base }: Readonly<{ base: string }>) {
  const {
    groups,
    catalogue,
    kind,
    isLoading,
    permissionsFailed,
    organizationQuery,
  } = useOrganizationSettingsGroups();
  const resolvedBase = useResolvedBase(base);
  const { backTo, backTabLabel } = useBackNav(base);
  const withheld = useWithheldSection(base, catalogue, groups);
  const orgless = useIsOrgless();
  if (orgless) {
    return (
      <OrganizationEntry fallback={`${resolvedBase}/${ACCOUNT_SETTINGS}`} />
    );
  }
  if (failedToLoad(organizationQuery)) {
    return <OrganizationLoadFailed query={organizationQuery} />;
  }
  // Permissions haven't resolved yet: an empty list here could just as easily mean "not
  // loaded" as "not entitled", so wait rather than eject an authorized admin on a cold
  // mount (a hard refresh, or a bookmarked /admin/* path landing here before anything
  // upstream has warmed the query).
  if (isLoading) return null;
  // Defensive only, effectively dead today: the tenant section carries no `permission`,
  // so `groups` always keeps at least the Organization/Community group for any member
  // scoped to an organization. Kept as a guard against a future catalogue shipping a kind
  // with zero unconditional sections, not because that case exists now.
  if (groups.length === 0) return <Navigate to={backTo} replace />;
  // A failed permissions read withholds everything; the section says so instead.
  if (withheld && !permissionsFailed) {
    return <Navigate to={`${resolvedBase}/${ORGANIZATION_SETTINGS}`} replace />;
  }
  return (
    <SettingsShell
      base={`${resolvedBase}/${ORGANIZATION_SETTINGS}`}
      title={organizationSettingsTitle(kind)}
      backTo={backTo}
      backTabLabel={backTabLabel}
      groups={groups}
    />
  );
}

function OrganizationSettingsOverview({ base }: Readonly<{ base: string }>) {
  const { groups, kind } = useOrganizationSettingsGroups();
  const resolvedBase = useResolvedBase(base);
  return (
    <SettingsLanding
      title={tenantWord(kind).label}
      base={`${resolvedBase}/${ORGANIZATION_SETTINGS}`}
      groups={groups}
    />
  );
}

/** Vocabulary needs the resolved settings base to build its tab links and its redirects,
 *  which `ORGANIZATION_SECTIONS`' bare `ComponentType` cannot carry - the same reason the
 *  account's privacy leaf is registered by hand. */
function VocabularyRoute({ base }: Readonly<{ base: string }>) {
  const resolvedBase = useResolvedBase(base);
  return <VocabularyPage base={`${resolvedBase}/${ORGANIZATION_SETTINGS}`} />;
}

/** Roles builds its own rail links, so it needs the resolved settings base for the same
 *  reason vocabulary does. */
function RolesRoute({ base }: Readonly<{ base: string }>) {
  const resolvedBase = useResolvedBase(base);
  return <RolesPage base={`${resolvedBase}/${ORGANIZATION_SETTINGS}`} />;
}

function organizationSettingsRoutes(
  base: string,
  sections: EditionSlots["settingsSections"],
): ReactNode {
  const orgBase = `${base}/${ORGANIZATION_SETTINGS}`;
  return (
    <Route element={<OrganizationSettingsShell base={base} />}>
      <Route
        path={orgBase}
        element={<OrganizationSettingsOverview base={base} />}
      />
      {Object.entries(ORGANIZATION_SECTIONS).map(([path, Section]) => (
        <Route key={path} path={`${orgBase}/${path}`} element={<Section />} />
      ))}
      {contributedSectionRoutes(orgBase, sections)}
      <Route
        path={`${orgBase}/${GROUP_ACCESS_PATH}`}
        element={<OrganizationGroupAccessRoute base={base} />}
      />
      <Route
        path={`${orgBase}/${ROLES_PATH}`}
        element={<RolesRoute base={base} />}
      />
      <Route
        path={`${orgBase}/${ROLES_PATH}/:roleId`}
        element={<RolesRoute base={base} />}
      />
      <Route
        path={`${orgBase}/${VOCABULARY_PATH}`}
        element={<VocabularyRoute base={base} />}
      />
      <Route
        path={`${orgBase}/${VOCABULARY_PATH}/:tab`}
        element={<VocabularyRoute base={base} />}
      />
    </Route>
  );
}

/**
 * The whole settings surface for one nesting level: the redirect table for every path
 * settings absorbed, then the account and organization subtrees. Called with `base: ""` at
 * the top level and `base: "/communities/:slug"` inside a hub - from the same function, so
 * the two nestings can never drift apart.
 *
 * `edition` names what the running edition adds; the shells read the same edition through
 * `useEdition()`, so `App` hands both the one value.
 */
export function SettingsRoutes({
  base,
  edition,
}: Readonly<{ base: string; edition: SettingsEdition }>): ReactNode {
  return (
    <>
      {movedRoutes(base, edition)}
      {accountSettingsRoutes(base, edition.accountPages)}
      {organizationSettingsRoutes(base, edition.settingsSections)}
    </>
  );
}
