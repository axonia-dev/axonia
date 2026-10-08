import { useLocation, useParams } from "react-router-dom";

import { useIsDelegated } from "@/auth/delegated-session";
import { useIsOrgless } from "@/auth/use-is-orgless";
import {
  ORGANIZATION_SETTINGS,
  organizationSettingsGroups,
  type SettingsGroup,
  visibleGroups,
} from "@/components/patterns/settings-nav";
import { useEdition } from "@/edition-context";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { usePermissions } from "@/features/admin/api/use-permissions";
import type { OrganizationKind } from "@/features/admin/domain/permission-catalog";
import { failedToLoad, type RetryableRead } from "@/lib/failed-to-load";

/** Resolves a `base`'s `:slug` placeholder against the actually matched param. Route `path`s
 *  match the placeholder natively, but a built href (`Navigate`, `Link`) needs the real value -
 *  a no-op for the top-level `base: ""`, which never carries the placeholder. Shared by
 *  `settings-routes.tsx` and `settings-routes.account-gates.tsx`, which would otherwise import
 *  from one another. */
export function useResolvedBase(base: string): string {
  const { slug = "" } = useParams();
  return base.replace(":slug", slug);
}

/** Privacy is a person's own data right, never the organization's: refused for a
 *  delegated session and for an enterprise session regardless of permission - see
 *  PrivacySettingsPage's own doc comment. Gating lives here, upstream of that page. A
 *  person with no organization holds the right over their own account alone, known at once.
 *
 *  `isLoading` distinguishes "not known yet" from "not entitled": `useCurrentOrganization`
 *  has no `initialData` and nothing upstream blocks on it, so the first render of a cold
 *  mount would otherwise read as `dataRights: false` and eject a hub member before their
 *  own organization's kind has loaded.
 *
 *  `failure` is set while that read has failed with nothing to answer from, through a retry
 *  too: whether the person holds the right is then unknown, not refused. */
export function useOwnDataRights(): {
  dataRights: boolean;
  isLoading: boolean;
  orgless: boolean;
  failure: RetryableRead | undefined;
} {
  const delegated = useIsDelegated();
  const orgless = useIsOrgless();
  const query = useCurrentOrganization();
  if (orgless) {
    return {
      dataRights: !delegated,
      isLoading: false,
      orgless,
      failure: undefined,
    };
  }
  const failed = failedToLoad(query);
  return {
    dataRights: query.data?.kind === "HUB" && !delegated,
    isLoading: query.data === undefined && !failed,
    orgless,
    failure: failed ? query : undefined,
  };
}

/** Whether this organization has an identity-provider directory a group binding could ever name.
 *  A hub brokers none - its members arrive by social or native sign-in, so no token reaching it
 *  carries a `groups` claim - and the backend answers its group listing with a 404 to match.
 *
 *  `isLoading` carries the same distinction as {@link useOwnDataRights}: "not known yet" is not
 *  "no directory", so a cold mount straight onto the route must wait rather than eject an
 *  enterprise administrator before their organization's kind has loaded. */
export function useOrganizationDirectory(): {
  hasDirectory: boolean;
  isLoading: boolean;
} {
  const { data: organization, isError } = useCurrentOrganization();
  return {
    hasDirectory: organization?.kind === "ENTERPRISE",
    isLoading: organization === undefined && !isError,
  };
}

/** The back tab: a top-level settings surface returns to the communities list, a hub's
 *  returns to that one community - so the label switches from plural to singular rather
 *  than reusing "Communities" where it would misname the destination. A person with no
 *  organization has no communities list, only the Discover lobby they came from. */
export function useBackNav(base: string) {
  const resolvedBase = useResolvedBase(base);
  const orgless = useIsOrgless();
  if (base !== "") return { backTo: resolvedBase, backTabLabel: "Community" };
  if (orgless) return { backTo: "/discover", backTabLabel: "Discover" };
  return { backTo: "/communities", backTabLabel: "Communities" };
}

/** `isLoading` mirrors `usePermissions`' own field, plus whether the organization's own
 *  `kind` has resolved: `useAccessMe` and `useCurrentOrganization` both have no
 *  `initialData`, so a cold mount's first render would otherwise read `can()` as false for
 *  every permission-gated section (emptying every group but the ungated tenant one) or
 *  default `kind` to ENTERPRISE before a hub member's own kind has loaded - either one
 *  would flash the wrong label. `kind` falls back to ENTERPRISE only while loading, when
 *  the result is discarded by the `isLoading` check at every call site. `permissionsFailed`
 *  tells a failed `/access/me` read apart from a refusal, since `can()` is false in both. */
export function useOrganizationSettingsGroups(): {
  groups: ReadonlyArray<SettingsGroup>;
  catalogue: ReadonlyArray<SettingsGroup>;
  kind: OrganizationKind;
  isLoading: boolean;
  permissionsFailed: boolean;
  organizationQuery: ReturnType<typeof useCurrentOrganization>;
} {
  const {
    can,
    isLoading: permissionsLoading,
    isError: permissionsFailed,
  } = usePermissions();
  const delegated = useIsDelegated();
  const organizationQuery = useCurrentOrganization();
  const { data: organization, isError } = organizationQuery;
  const { settingsSections, notificationsCard } = useEdition();
  const kind = organization?.kind ?? "ENTERPRISE";
  const catalogue = organizationSettingsGroups(
    kind,
    settingsSections,
    notificationsCard?.reveals,
  );
  const groups = visibleGroups(catalogue, {
    can,
    delegated,
  });
  const isLoading =
    permissionsLoading || (organization === undefined && !isError);
  return {
    groups,
    catalogue,
    kind,
    isLoading,
    permissionsFailed,
    organizationQuery,
  };
}

function offers(groups: ReadonlyArray<SettingsGroup>, path: string): boolean {
  return groups.some((group) => group.items.some((item) => item.path === path));
}

/** Whether the path names an organization section the rail withholds from this caller. The rail
 *  offers only what their permissions reach, and a typed or bookmarked path must not reach
 *  further: the page it opens would only fill with refusals. */
export function useWithheldSection(
  base: string,
  catalogue: ReadonlyArray<SettingsGroup>,
  visible: ReadonlyArray<SettingsGroup>,
): boolean {
  const { pathname } = useLocation();
  const prefix = `${useResolvedBase(base)}/${ORGANIZATION_SETTINGS}/`;
  if (!pathname.startsWith(prefix)) return false;
  const section = pathname.slice(prefix.length).split("/")[0] ?? "";
  return offers(catalogue, section) && !offers(visible, section);
}
