import { useEffect } from "react";

import { type RefusalSeam, useHeldRefusals } from "./held-refusals";
import type { AuthContextProps } from "./session";

export type { RefusalSeam };

/** What an app's guard draws, beyond its children or its wait for the first renewal. */
interface SessionGuard {
  /**
   * A page kept through an identity provider outage after one of its requests was refused: the
   * guard keeps the page and says, beside it, that sign-in is unavailable.
   */
  keptThroughOutage: boolean;
  /**
   * The server refused the token a sign-in this tab started moments ago brought back. The guard
   * shows that in place of the page, with a way to sign in again, rather than leaving at once.
   */
  refusedAfterSignIn: boolean;
}

/**
 * The part of an app's guard that is the same in every app: when a 401 is held, dropped or
 * reported, and when to leave for a sign-in. `signIn` is the app's own, since only the app knows
 * what a login must carry.
 */
export function useSessionGuard(
  auth: AuthContextProps,
  seam: RefusalSeam,
  signIn: () => void,
): SessionGuard {
  const { held, refusedAfterSignIn } = useHeldRefusals(auth, seam);

  useEffect(() => {
    if (
      auth.isAuthenticated ||
      auth.isLoading ||
      auth.activeNavigator ||
      auth.signinUnavailable ||
      refusedAfterSignIn
    ) {
      return;
    }

    // Nothing is tried before this, and nothing needs to be. The provider has already spent the
    // sealed cookie on the way in: reaching here means the edge answered that there is no session
    // left to renew, or a 401 dropped the token. While the identity provider cannot be reached the
    // guard waits for the scheduled renewal instead, since a login could not complete, and once
    // the server has refused a fresh sign-in's token another sign-in is the person's choice.
    signIn();
  }, [auth, signIn, refusedAfterSignIn]);

  return {
    keptThroughOutage: auth.isAuthenticated && auth.signinUnavailable && held,
    refusedAfterSignIn,
  };
}
