import { type Query, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";

import type { AuthContextProps } from "./session";

/**
 * Where an app's requests meet the session: the one slot its API client reports a 401 to, with the
 * token the refused request carried, and how it tells a read refused for its token from any other
 * failure.
 */
export interface RefusalSeam {
  setUnauthorizedHandler: (
    handler: ((sentToken: string | undefined) => void) | undefined,
  ) => void;
  isRefusal: (error: unknown) => boolean;
}

interface HeldRefusals {
  /** A 401 for the token in hand is held until a renewal decides. */
  held: boolean;
  /** The server refused the token a sign-in this tab started moments ago brought back. */
  refusedAfterSignIn: boolean;
}

/**
 * Answers a 401, and says whether one is held. A 401 means the token no longer convinces the
 * backend. `removeUser` drops this tab's copy and nothing else - it schedules no renewal - so the
 * guard sees a settled, unauthenticated state on the same pass and starts a full login. That is a
 * redirect rather than a silent renewal: where the identity provider's session is still alive the
 * person comes straight back, and where it is not, a renewal could not have helped either.
 *
 * While the identity provider cannot be reached, or a renewal for an expired token is under way,
 * the 401 is held instead: leaving would lose what is on the page, for a sign-in that could not
 * complete or a token that is about to arrive. The renewal decides. A fresh token carries on and
 * the reads the 401 refused load again, `unavailable` keeps holding, and a refusal drops the token.
 *
 * A 401 for a token that is no longer the current one is stale whenever it lands: the request was
 * sent before a renewal and answered after it, even before the render that publishes the new one,
 * which is why the session is asked for its token rather than the page for the one it rendered.
 * It says nothing about the token in hand, so it is never a reason to leave and raises no notice by
 * itself. The read it refused is asked again at once with the current token, outage or not: a token
 * that still works answers it, and one that is refused meets the branches above like any other 401.
 *
 * A 401 for the token a sign-in brought back moments ago is the server refusing the session, as
 * when it is configured for another audience or issuer. Another sign-in would come straight back
 * with another token it refuses, round and round, so the token is kept and the refusal reported.
 * A request that carried no token was refused nothing a sign-in brought back, so it never counts.
 */
export function useHeldRefusals(
  auth: AuthContextProps,
  seam: RefusalSeam,
): HeldRefusals {
  const queryClient = useQueryClient();
  const holding = auth.signinUnavailable || auth.renewalPending;
  const [held, setHeld] = useState(false);
  const [refusedAfterSignIn, setRefusedAfterSignIn] = useState(false);
  const [staleRefusals, setStaleRefusals] = useState(0);
  const reloadOnRecovery = useRef(false);
  const { removeUser, holdsRefusals, currentAccessToken, signedInMomentsAgo } =
    auth;
  if (held && !holding) {
    setHeld(false);
  }

  // Asks the session rather than reading `holding`: a renewal that began after the last render
  // holds a 401 that lands before the next one.
  useEffect(() => {
    seam.setUnauthorizedHandler((sentToken) => {
      if (sentToken !== currentAccessToken()) {
        setStaleRefusals((count) => count + 1);
      } else if (holdsRefusals()) {
        setHeld(true);
        reloadOnRecovery.current = true;
      } else if (sentToken !== undefined && signedInMomentsAgo()) {
        setRefusedAfterSignIn(true);
      } else {
        void removeUser();
      }
    });
    return () => seam.setUnauthorizedHandler(undefined);
  }, [seam, removeUser, holdsRefusals, currentAccessToken, signedInMomentsAgo]);

  // Runs after the render that published the fresh token, so the reload carries it. Only reads
  // refused for want of a token are asked again; nothing is written twice.
  useEffect(() => {
    if (holding || !auth.isAuthenticated || !reloadOnRecovery.current) return;
    reloadOnRecovery.current = false;
    void queryClient.invalidateQueries({ predicate: refusedBy(seam) });
  }, [holding, auth.isAuthenticated, queryClient, seam]);

  // Runs once per stale refusal, after it has settled into its read.
  useEffect(() => {
    if (staleRefusals === 0) return;
    void queryClient.invalidateQueries({ predicate: refusedBy(seam) });
  }, [queryClient, staleRefusals, seam]);

  return { held, refusedAfterSignIn };
}

/** A read refused only because the token it rode on had expired: a fresh token will answer it. */
const refusedBy =
  (seam: RefusalSeam) =>
  (query: Query): boolean =>
    query.state.status === "error" && seam.isRefusal(query.state.error);
