// The wire shape of token-scoped Organization switching, isolated here so the rest of the
// app depends on intent, not on Keycloak's encoding. Verified against a live Keycloak 26.3:
//   - the active Organization is the flat string `org` claim, emitted by an
//     organization-membership mapper with `multivalued=false` and `claim.name=org`;
//   - a token is scoped to one Organization by the OIDC scope `organization:<alias>`.
// Two hard facts about that scope shape the switch design (both confirmed against a live
// Keycloak realm):
//   - a *refresh* grant can only narrow scope, so it can NEVER add an `organization:<alias>`
//     that login did not grant. Switching therefore re-authorizes at the authorize endpoint
//     (`signinRedirect`), not via silent refresh: a silent refresh just drops the org and
//     nothing changes.
//   - automatic silent renew narrows back to `settings.scope`, so the active Organization
//     must live in the *startup* scope to survive a renew. We persist the choice in
//     localStorage and fold it into the bootstrap scope; switching rewrites it and
//     re-authorizes.
// Keep all of this quarantined to this seam: dependencies point inward toward intent, and
// adapters own the edges.

import { decodeJwtPayload } from "@/auth/core/jwt";

/** The OIDC scopes every token requests, before any active-Organization scope is appended. */
const OIDC_BASE_SCOPE = "openid profile email";

/** Where the chosen active Organization persists, so login + silent renew stay scoped to it. */
const ACTIVE_ORG_STORAGE_KEY = "axonia.active-organization";

/**
 * A one-shot marker that the sole-Organization auto-enter already fired. It is never cleared by an
 * org-less sign-in: the auto-enter's own redirect is what establishes the session, so clearing it
 * there would let a token that comes back org-less (Keycloak granting the scope but emitting no
 * `org` claim) re-trigger the redirect forever. A new token that does name an organization is the
 * entry working, so that re-arms it (see {@link rearmAutoEnterOnOrganizationToken}).
 */
const AUTO_ENTER_STORAGE_KEY = "axonia.auto-enter-attempted";

/** A session-scoped one-shot flag: survives an authorize round-trip, resets with the tab. */
function sessionFlag(key: string) {
  return {
    isSet(): boolean {
      try {
        return window.sessionStorage.getItem(key) !== null;
      } catch {
        return false;
      }
    },
    set(): void {
      try {
        window.sessionStorage.setItem(key, "1");
      } catch {
        // A blocked sessionStorage only weakens the loop guard; it still holds per page load.
      }
    },
    clear(): void {
      try {
        window.sessionStorage.removeItem(key);
      } catch {
        // Nothing persisted means nothing to clear.
      }
    },
  };
}

const autoEnterFlag = sessionFlag(AUTO_ENTER_STORAGE_KEY);

let delegatedOrganizationId: string | undefined;

/**
 * Publishes the Organization a delegated tab acts in, or withdraws it with `undefined`. Set during
 * render by the frame that holds the delegated bearer, beside the bearer itself, so the two are
 * published and withdrawn together.
 */
export function setDelegatedOrganizationId(
  organizationId: string | undefined,
): void {
  delegatedOrganizationId = organizationId;
}

/**
 * The active Organization is the flat string `org` claim on the access token. Returns
 * undefined when there is no token, no claim, or the token is unparseable.
 *
 * A delegated bearer is opaque - the backend issues it, so it carries no claims at all - and the
 * frame holding it publishes the session's Organization through {@link setDelegatedOrganizationId}.
 * Reading it here rather than at each of the seven call sites is what keeps a delegated tab from
 * looking Organization-less and being sent to sign in by the lobby's sole-Organization auto-enter.
 */
export function activeOrganizationId(
  accessToken: string | undefined,
): string | undefined {
  if (delegatedOrganizationId !== undefined) return delegatedOrganizationId;
  if (accessToken === undefined) return undefined;
  const org = decodeJwtPayload(accessToken)?.["org"];
  return typeof org === "string" ? org : undefined;
}

/**
 * The OIDC `scope` that scopes a token to a single Organization: the base scopes plus
 * `organization:<alias>`. Keycloak then issues a token whose `org` claim is that alias.
 */
export function organizationScope(orgId: string): string {
  return `${OIDC_BASE_SCOPE} organization:${orgId}`;
}

/** The Organization the caller last switched into, persisted across reloads (undefined if never set). */
export function storedActiveOrganizationId(): string | undefined {
  try {
    return window.localStorage.getItem(ACTIVE_ORG_STORAGE_KEY) ?? undefined;
  } catch {
    return undefined;
  }
}

/** Persist the chosen active Organization so the next bootstrap scopes login + renew to it. */
export function setStoredActiveOrganizationId(orgId: string): void {
  try {
    window.localStorage.setItem(ACTIVE_ORG_STORAGE_KEY, orgId);
  } catch {
    // A blocked localStorage only costs persistence across reloads; the switch still works.
  }
}

/** Forgets the persisted active organization, for when that organization no longer exists. */
export function clearStoredActiveOrganizationId(): void {
  try {
    window.localStorage.removeItem(ACTIVE_ORG_STORAGE_KEY);
  } catch {
    // A blocked localStorage never had the value persisted in the first place.
  }
}

/** The OIDC scope for acting within no organization at all (the fresh-sign-in state). */
export function organizationlessScope(): string {
  return OIDC_BASE_SCOPE;
}

// The invalid_scope loop guard that used to live here is gone, along with the error-shape sniffing
// that fed it. Keycloak refuses a dead `organization:<alias>` at the authorize request, which the
// browser no longer makes: edge/src/auth.ts sees that refusal, drops the organization and restarts
// the login once, under its own one-shot guard. AuthScopeRecovery reconciles what was remembered
// here with what the returned token actually holds.

/** Whether the sole-Organization auto-enter already fired this session (loop guard). */
export function autoEnterAttempted(): boolean {
  return autoEnterFlag.isSet();
}

/** Record that the auto-enter fired, so an org-less token cannot spin a redirect loop. */
export function markAutoEnterAttempted(): void {
  autoEnterFlag.set();
}

/** Spend the guard when the session it belongs to ends, so the next member auto-enters too. */
export function clearAutoEnterFlag(): void {
  autoEnterFlag.clear();
}

let lastSeenToken: string | undefined;

/**
 * Re-arms the one-shot entry when a new token names an organization: the entry worked, so a later
 * org-less spell in the same tab (after leaving or deleting a hub) may enter again. Only a new
 * token counts. A guard claimed while still holding an organization's token, as a member choosing
 * another organization does before signing in org-less, must survive the renders before the tab
 * leaves.
 */
export function rearmAutoEnterOnOrganizationToken(
  accessToken: string | undefined,
): void {
  if (accessToken === lastSeenToken) return;
  lastSeenToken = accessToken;
  if (accessToken === undefined) return;
  if (typeof decodeJwtPayload(accessToken)?.["org"] === "string") {
    autoEnterFlag.clear();
  }
}

/**
 * The OIDC scope to request at app bootstrap: the base scopes plus the Organization the caller
 * last switched into. Folding it in here (rather than only at switch time) is what makes both
 * the initial login and every automatic silent renew stay scoped to it, instead of narrowing
 * the `org` claim away.
 *
 * With nothing persisted we request NO organization scope. Keycloak only accepts
 * `organization:<alias>` for an Organization that actually exists in the realm - any guessed
 * alias fails the whole authorize request with `invalid_scope`, locking the caller out before
 * they ever reach an identity provider. A caller with no stored choice therefore signs in
 * Organization-less and lands in the Discover lobby, which picks their Organization from live
 * membership (see {@link bootstrapOrganization}).
 */
export function startupScope(): string {
  const stored = storedActiveOrganizationId();
  return stored === undefined
    ? organizationlessScope()
    : organizationScope(stored);
}
