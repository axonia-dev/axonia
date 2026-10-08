// A guard as an app writes one around the session core, with nothing of either app's: its own 401
// slot, its own refused-read error, and plain text where an app draws its notice and its states.

import type { ReactNode } from "react";
import { vi } from "vitest";

import type { AuthContextProps } from "@/auth/core/session";
import { type RefusalSeam, useSessionGuard } from "@/auth/core/session-guard";

let handler: ((sentToken: string | undefined) => void) | undefined;

/** What the shell's API client throws for a read the backend refused for its token. */
export class Refusal extends Error {}

const SEAM: RefusalSeam = {
  setUnauthorizedHandler: (next) => {
    handler = next;
  },
  isRefusal: (error) => error instanceof Refusal,
};

/** The shell's API client reporting a 401 for a request that carried `sentToken`. */
export function refuse(sentToken: string | undefined): void {
  handler?.(sentToken);
}

export const KEPT_NOTICE = "kept through an outage";
export const REFUSED_STATE = "refused after signing in";
export const SIGNING_IN = "signing in";

export function ShellGuard({
  auth,
  signIn,
  children,
}: Readonly<{
  auth: AuthContextProps;
  signIn: () => void;
  children: ReactNode;
}>) {
  const { keptThroughOutage, refusedAfterSignIn } = useSessionGuard(
    auth,
    SEAM,
    signIn,
  );
  if (refusedAfterSignIn) return <p>{REFUSED_STATE}</p>;
  if (!auth.isAuthenticated) return <p>{SIGNING_IN}</p>;
  return (
    <>
      {children}
      {keptThroughOutage && <p>{KEPT_NOTICE}</p>}
    </>
  );
}

/**
 * The session as a guard reads it, signed in and settled unless `overrides` say otherwise. Its
 * current token is the one it holds, as the session's own is.
 */
export function authState(
  overrides: Partial<AuthContextProps> = {},
): AuthContextProps {
  const state: AuthContextProps = {
    user: { access_token: "a-token", profile: { sub: "alice-sub" } },
    isAuthenticated: true,
    isLoading: false,
    activeNavigator: undefined,
    error: undefined,
    signinRedirect: vi.fn(),
    signoutRedirect: vi.fn(),
    removeUser: vi.fn(),
    signinSilent: vi.fn(),
    signinUnavailable: false,
    renewalPending: false,
    holdsRefusals: () => false,
    currentAccessToken: () => state.user?.access_token,
    signedInMomentsAgo: () => false,
    ...overrides,
  };
  return state;
}
