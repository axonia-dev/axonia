import { type ReactNode, useCallback, useEffect, useRef } from "react";

import { forgetStateOfPreviousAccount } from "@/auth/account-state";
import {
  rearmAutoEnterOnOrganizationToken,
  startupScope,
} from "@/auth/active-organization";
import { type RefusalSeam, useSessionGuard } from "@/auth/core/session-guard";
import { setCurrentUserId } from "@/auth/current-user";
import { useAuth } from "@/auth/session";
import { SessionRefused } from "@/auth/SessionRefused";
import {
  SIGNIN_UNAVAILABLE,
  SigningInOrUnavailable,
} from "@/auth/SigninUnavailable";
import { setAccessToken, setUnauthorizedHandler } from "@/auth/token";
import { toastQueue } from "@/components/ui/toast";
import { ApiError } from "@/lib/api-client";

const REFUSALS: RefusalSeam = {
  setUnauthorizedHandler,
  isRefusal: (error) => error instanceof ApiError && error.status === 401,
};

export function AuthGuard({ children }: Readonly<{ children: ReactNode }>) {
  const auth = useAuth();

  // Published during render (not in an effect) so the seams are current before
  // children mount and fire their first queries. Idempotent, StrictMode-safe.
  setAccessToken(auth.user?.access_token);
  setCurrentUserId(auth.user?.profile.sub);
  // Render, not an effect, for the same reason and one more: a child's effect runs before its
  // parent's, so the lobby would read a spent auto-enter guard before an effect here could drop
  // it, and strand the arriving member in the lobby for that whole page load.
  forgetStateOfPreviousAccount(auth.user?.profile.sub);
  // Render too: an entry gate snapshots the one-shot flag at mount, before a parent effect runs.
  rearmAutoEnterOnOrganizationToken(auth.user?.access_token);

  // The organization scope rides every login because a refresh grant can only narrow scope,
  // never add one.
  const signIn = useCallback((): void => {
    void auth.signinRedirect({ scope: startupScope() });
  }, [auth]);
  const latestSignIn = useRef(signIn);
  useEffect(() => {
    latestSignIn.current = signIn;
  });

  const { keptThroughOutage, refusedAfterSignIn } = useSessionGuard(
    auth,
    REFUSALS,
    signIn,
  );

  // A page kept through the outage hears about it in a notice beside it, with the same way out.
  useEffect(() => {
    if (!keptThroughOutage) return undefined;
    const notice = toastQueue.add({
      title: SIGNIN_UNAVAILABLE,
      tone: "neutral",
      action: { label: "Sign in", onPress: () => latestSignIn.current() },
    });
    return () => toastQueue.close(notice);
  }, [keptThroughOutage]);

  if (refusedAfterSignIn) {
    return <SessionRefused onSignIn={signIn} />;
  }

  if (!auth.isAuthenticated) {
    return (
      <SigningInOrUnavailable
        unavailable={auth.signinUnavailable}
        onSignIn={signIn}
      />
    );
  }

  return <>{children}</>;
}
