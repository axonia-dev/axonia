import type { ReactNode } from "react";

import { useIsDelegated } from "@/auth/delegated-session";
import {
  type SettingsGroup,
  type SettingsNavItem,
  visibleGroups,
} from "@/components/patterns/settings-nav";
import { accountSettingsGroups } from "@/components/patterns/settings-nav.account";
import type { AccountPage } from "@/edition";
import { useEdition } from "@/edition-context";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useAgentAccess } from "@/features/agent/api/use-personal-access-tokens";
import { useOwnDataRights } from "@/routes/settings-routes.hooks";

type Pages = ReadonlyArray<AccountPage>;
type Groups = ReadonlyArray<SettingsGroup>;

function PageOffer({
  page,
  children,
}: Readonly<{ page: AccountPage; children: (offered: boolean) => ReactNode }>) {
  const { useOffer } = page;
  const { offered } = useOffer();
  return children(offered);
}

/** Asks every page whether it is offered, one component per page so each `useOffer` is a hook
 *  call of its own, and hands the offered ones on in the edition's order. */
function OfferedAccountPages({
  pages,
  children,
}: Readonly<{ pages: Pages; children: (offered: Pages) => ReactNode }>) {
  const [first, ...rest] = pages;
  if (first === undefined) return <>{children([])}</>;
  return (
    <PageOffer page={first}>
      {(isOffered) => (
        <OfferedAccountPages pages={rest}>
          {(offered) => children(isOffered ? [first, ...offered] : offered)}
        </OfferedAccountPages>
      )}
    </PageOffer>
  );
}

function contributedGroups(offered: Pages): Groups {
  const own = offered.filter((page) => page.joins === undefined);
  const labels = [...new Set(own.map((page) => page.groupLabel))];
  return labels.map((label): SettingsGroup => ({
    label,
    chrome: "divider",
    items: own
      .filter((page) => page.groupLabel === label)
      .map((page) => page.item),
  }));
}

function joiningAccount(offered: Pages): ReadonlyArray<SettingsNavItem> {
  return offered
    .filter((page) => page.joins === "account")
    .map((page) => page.item);
}

function VisibleAccountGroups({
  offered,
  children,
}: Readonly<{ offered: Pages; children: (groups: Groups) => ReactNode }>) {
  const { can } = usePermissions();
  const delegated = useIsDelegated();
  const { dataRights, orgless } = useOwnDataRights();
  const { available: agentAccess } = useAgentAccess();
  return children(
    visibleGroups(
      accountSettingsGroups(
        { dataRights, agentAccess, orgless },
        contributedGroups(offered),
        joiningAccount(offered),
      ),
      { can, delegated },
    ),
  );
}

/** The account rail's groups for this caller: the account's own rows, then every page the
 *  edition adds and offers this caller, each group behind a divider. */
export function AccountGroups({
  children,
}: Readonly<{ children: (groups: Groups) => ReactNode }>) {
  const { accountPages } = useEdition();
  return (
    <OfferedAccountPages pages={accountPages}>
      {(offered) => (
        <VisibleAccountGroups offered={offered}>
          {children}
        </VisibleAccountGroups>
      )}
    </OfferedAccountPages>
  );
}
