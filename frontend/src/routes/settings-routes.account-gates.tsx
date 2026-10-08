import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";

import { useIsOrgless } from "@/auth/use-is-orgless";
import { PageLoadFailed } from "@/components/patterns/PageLoadFailed";
import { ACCOUNT_SETTINGS } from "@/components/patterns/settings-nav";
import type { AccountPage } from "@/edition";
import { useAgentAccess } from "@/features/agent/api/use-personal-access-tokens";
import { ApiKeysView } from "@/features/agent/ui/ApiKeysView";
import { PrivacySettingsPage } from "@/features/privacy/ui/PrivacySettingsPage";
import type { RetryableRead } from "@/lib/failed-to-load";
import { OrganizationEntry } from "@/routes/organization-entry";
import {
  useOwnDataRights,
  useResolvedBase,
} from "@/routes/settings-routes.hooks";

/** Shared shape behind every account leaf offered only when the caller earns it: wait while
 *  `isLoading` rather than reading "not answered yet" as "not offered", otherwise redirect
 *  away on `!offered`, otherwise render the leaf. A hard refresh straight onto the route must
 *  never eject the caller before the query it depends on has resolved, and a read that failed
 *  is not a refusal either: it is said on the page, under the leaf's own heading. */
function GatedAccountRoute({
  base,
  offered,
  isLoading,
  failure,
  children,
}: Readonly<{
  base: string;
  offered: boolean;
  isLoading: boolean;
  failure?:
    | Readonly<{ read: RetryableRead; title: string; message: string }>
    | undefined;
  children: ReactNode;
}>): ReactNode {
  const resolvedBase = useResolvedBase(base);
  if (failure !== undefined) {
    return (
      <PageLoadFailed
        title={failure.title}
        message={failure.message}
        query={failure.read}
      />
    );
  }
  if (isLoading) return null;
  if (!offered) {
    return <Navigate to={`${resolvedBase}/${ACCOUNT_SETTINGS}`} replace />;
  }
  return <>{children}</>;
}

/** Privacy is a member's own data right, never the organization's: refused for a
 *  delegated session and for an enterprise session regardless of permission - see
 *  PrivacySettingsPage's own doc comment. */
export function AccountPrivacyRoute({ base }: Readonly<{ base: string }>) {
  const { dataRights, isLoading, orgless, failure } = useOwnDataRights();
  return (
    <GatedAccountRoute
      base={base}
      offered={dataRights}
      isLoading={isLoading}
      failure={
        failure && {
          read: failure,
          title: "Privacy",
          message: "Could not load the organization.",
        }
      }
    >
      <PrivacySettingsPage personal={orgless} />
    </GatedAccountRoute>
  );
}

/** Profile and Notifications are held per organization: a session with none enters one first,
 *  and a person who has none is sent to the account overview. */
export function AccountOrganizationRoute({
  base,
  children,
}: Readonly<{ base: string; children: ReactNode }>) {
  const resolvedBase = useResolvedBase(base);
  if (useIsOrgless()) {
    return (
      <OrganizationEntry fallback={`${resolvedBase}/${ACCOUNT_SETTINGS}`} />
    );
  }
  return <>{children}</>;
}

/** API keys is offered only where agents may connect at all. */
export function AccountApiKeysRoute({ base }: Readonly<{ base: string }>) {
  const { available, isLoading, failure } = useAgentAccess();
  return (
    <GatedAccountRoute
      base={base}
      offered={available}
      isLoading={isLoading}
      failure={
        failure && {
          read: failure,
          title: "API keys",
          message: "Could not load the API keys.",
        }
      }
    >
      <ApiKeysView />
    </GatedAccountRoute>
  );
}

/** A page an edition adds, offered where its own `useOffer` says so. */
export function AccountPageRoute({
  base,
  page,
}: Readonly<{ base: string; page: AccountPage }>) {
  const { useOffer, Page } = page;
  const { offered, isLoading, failure } = useOffer();
  return (
    <GatedAccountRoute
      base={base}
      offered={offered}
      isLoading={isLoading}
      failure={
        failure && {
          read: failure,
          title: page.item.label,
          message: "Could not load this page.",
        }
      }
    >
      <Page />
    </GatedAccountRoute>
  );
}
