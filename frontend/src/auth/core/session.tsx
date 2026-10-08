// The session, as every application running it sees it, each under its own names. The
// shape is the one react-oidc-context exposed, so every call site reads as it did. There is no stored
// user and no refresh token in this process: the access token lives in React state for as long as
// the tab does, and the credential that outlives it is a sealed cookie only edge/src/auth.ts opens.

import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import { markSignInStarted, signInStartedRecently } from "./recent-sign-in";
import { delayFor, retryDelayMs } from "./renewal-delays";
import {
  type AuthUser,
  endSession,
  type RenewalUnavailable,
  type RenewedSession,
  renewOnce,
  startLogin,
} from "./session-client";

export type { AuthUser };

export interface SigninArgs {
  state?: { returnTo?: string };
  scope?: string;
}

export interface AuthContextProps {
  user: AuthUser | undefined;
  isAuthenticated: boolean;
  isLoading: boolean;
  activeNavigator: "signinRedirect" | "signoutRedirect" | undefined;
  error: Error | undefined;
  signinRedirect: (args?: SigninArgs) => Promise<void>;
  signoutRedirect: () => Promise<void>;
  removeUser: () => Promise<void>;
  signinSilent: () => Promise<AuthUser | undefined>;
  /**
   * The last renewal could not be made because the identity provider could not be reached, and
   * another is scheduled. A sign-in could not complete either, so nothing should start one.
   */
  signinUnavailable: boolean;
  /**
   * A renewal is under way for a token that has already expired, as on a tab back from the
   * background or from sleep. A 401 meanwhile is that expiry, not the backend refusing anybody.
   */
  renewalPending: boolean;
  /**
   * Whether a 401 for the token in hand waits for a renewal rather than leaving for a sign-in: the
   * identity provider could not be reached, or a renewal for an expired token is under way. Read
   * from the session the moment it is asked, so it already answers yes for a renewal React has not
   * rendered yet.
   */
  holdsRefusals: () => boolean;
  /** The token held now, not the one last rendered: a 401 for one a renewal replaced is stale. */
  currentAccessToken: () => string | undefined;
  /** This tab left for a sign-in under a minute ago, so a 401 now is the server refusing it. */
  signedInMomentsAgo: () => boolean;
}

/**
 * Each app's own names for what its tabs share: the `BroadcastChannel` a tab that renewed tells the
 * others on, the Web Lock that runs one renewal at a time, and the sessionStorage key that
 * remembers when this tab last left for a sign-in.
 */
export interface SessionNames {
  channel: string;
  lock: string;
  signInStarted: string;
}

const AuthContext = createContext<AuthContextProps | undefined>(undefined);

const isUnavailable = (
  renewed: RenewedSession | RenewalUnavailable | null,
): renewed is RenewalUnavailable =>
  renewed !== null && "unavailable" in renewed;

/**
 * `probe` false settles signed out without asking the edge: for a page that is only ever opened
 * without a session, where the edge could only refuse the cookie a sign-out left behind.
 */
export function AuthProvider({
  children,
  names,
  probe = true,
}: Readonly<{
  children: ReactNode;
  names: SessionNames;
  probe?: boolean | undefined;
}>) {
  const [user, setUser] = useState<AuthUser | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(probe);
  const [activeNavigator, setActiveNavigator] =
    useState<AuthContextProps["activeNavigator"]>(undefined);
  const [signinUnavailable, setSigninUnavailable] = useState(false);
  const [renewalPending, setRenewalPending] = useState(false);
  const unavailable = useRef(false);
  const lapsedRenewals = useRef(0);
  const renewalsInFlight = useRef(0);
  const renewedChannel = useRef<BroadcastChannel | undefined>(undefined);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  // What renew answers when the provider could not be reached: the person it already holds.
  const held = useRef<AuthUser | undefined>(undefined);
  // When the held token stops working, and how many renewals have failed since, which is what the
  // retry backs off on.
  const expiresAt = useRef(0);
  const failuresSinceExpiry = useRef(0);
  // The scheduled renewal has to reach the newest renew without renew referring to itself while it
  // is still being defined. A ref is the seam: the timer calls through it, so each renewal schedules
  // the next without the function closing over its own binding.
  const renewRef = useRef<(() => Promise<AuthUser | undefined>) | undefined>(
    undefined,
  );

  // A provider that could not be reached keeps the token in hand, which works until it expires, and
  // asks again on a timer. One timer per tab, re-armed rather than added to, so a retry replaces the
  // scheduled renewal instead of running beside it.
  const renew = useCallback(async (): Promise<AuthUser | undefined> => {
    const lapsed =
      held.current !== undefined && Date.now() >= expiresAt.current;
    if (lapsed) lapsedRenewals.current += 1;
    renewalsInFlight.current += 1;
    setRenewalPending(lapsedRenewals.current > 0);
    let renewed: RenewedSession | RenewalUnavailable | null;
    try {
      renewed = await renewOnce(names.lock);
    } finally {
      if (lapsed) lapsedRenewals.current -= 1;
      renewalsInFlight.current -= 1;
      setRenewalPending(lapsedRenewals.current > 0);
    }
    const schedule = (delayMs: number): void => {
      clearTimeout(timer.current);
      timer.current = setTimeout(() => void renewRef.current?.(), delayMs);
    };
    if (isUnavailable(renewed)) {
      if (Date.now() >= expiresAt.current) failuresSinceExpiry.current += 1;
      unavailable.current = true;
      setSigninUnavailable(true);
      schedule(
        retryDelayMs(failuresSinceExpiry.current, renewed.retryAfterSeconds),
      );
      return held.current;
    }
    failuresSinceExpiry.current = 0;
    unavailable.current = false;
    setSigninUnavailable(false);
    held.current = renewed?.user;
    setUser(held.current);
    if (renewed === null) return undefined;
    expiresAt.current = Date.now() + renewed.expiresIn * 1000;
    schedule(delayFor(renewed.expiresIn));
    renewedChannel.current?.postMessage("renewed");
    return renewed.user;
  }, [names.lock]);

  const holdsRefusals = useCallback(
    () => unavailable.current || lapsedRenewals.current > 0,
    [],
  );

  const currentAccessToken = useCallback(() => held.current?.access_token, []);

  const signedInMomentsAgo = useCallback(
    () => signInStartedRecently(names.signInStarted),
    [names.signInStarted],
  );

  // One renewal on mount is the whole of "am I signed in": there is nothing stored to read, so the
  // only way to find out is to ask the edge whether the cookie it cannot see is still good.
  useEffect(() => {
    renewRef.current = renew;
    if (!probe) return undefined;
    let unmounted = false;
    void (async () => {
      await renew();
      // Guarded, because a tab closed mid-renewal would otherwise settle a component that is no
      // longer mounted, and the renewal is the slowest thing that happens on a page load.
      if (!unmounted) setIsLoading(false);
    })();
    return () => {
      unmounted = true;
      clearTimeout(timer.current);
    };
  }, [renew, probe]);

  // A background tab's timers are throttled and a sleeping laptop's do not fire at all, so a tab
  // returning to the foreground can be holding a token that expired hours ago. Renewing on the way
  // back costs one request and is the difference between resuming work and a page of failed calls.
  // A device back on the network asks at once too: the failures it backed off on were the network's.
  // A laptop waking from sleep usually reports its network back, even where the tab was never hidden.
  useEffect(() => {
    if (!probe) return undefined;
    const askNow = (): void => {
      // Somebody is looking again, or the network is back, so the back-off starts over.
      failuresSinceExpiry.current = 0;
      void renew();
    };
    const onVisible = (): void => {
      if (document.visibilityState === "visible") askNow();
    };
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("online", askNow);
    return () => {
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("online", askNow);
    };
  }, [renew, probe]);

  // A sibling tab that renewed proves the provider is back, so a tab still waiting out the outage
  // asks now rather than at the end of its back-off, up to six minutes later. Not while a renewal of
  // its own is under way: that one decides, and the browser runs one renewal at a time, so a tab that
  // asked on every sibling's answer would queue one more renewal per tab ahead of it.
  useEffect(() => {
    if (!probe || typeof BroadcastChannel === "undefined") return undefined;
    const channel = new BroadcastChannel(names.channel);
    renewedChannel.current = channel;
    channel.onmessage = () => {
      if (unavailable.current && renewalsInFlight.current === 0) void renew();
    };
    return () => {
      renewedChannel.current = undefined;
      channel.close();
    };
  }, [renew, probe, names.channel]);

  const value = useMemo<AuthContextProps>(
    () => ({
      user,
      isAuthenticated: user !== undefined,
      isLoading,
      activeNavigator,
      error: undefined,
      // Both keep the promise-returning shape their call sites already had, even though leaving
      // the page is synchronous: the alternative was editing every call site to say something
      // different about a navigation that behaves exactly as it did before. Each one is remembered,
      // so a token the server refuses straight after it ends in a message rather than a loop.
      signinRedirect: (args?: SigninArgs) => {
        setActiveNavigator("signinRedirect");
        markSignInStarted(names.signInStarted);
        startLogin({
          ...(args?.state?.returnTo === undefined
            ? {}
            : { returnTo: args.state.returnTo }),
          ...(args?.scope === undefined ? {} : { scope: args.scope }),
        });
        return Promise.resolve();
      },
      signoutRedirect: () => {
        setActiveNavigator("signoutRedirect");
        return endSession();
      },
      // Drops this tab's token without touching the cookie. The 401 handler uses it to force the
      // guard to renew, which is the right answer to a token the backend has stopped accepting.
      removeUser: () => {
        held.current = undefined;
        setUser(undefined);
        return Promise.resolve();
      },
      signinSilent: renew,
      signinUnavailable,
      renewalPending,
      holdsRefusals,
      currentAccessToken,
      signedInMomentsAgo,
    }),
    [
      user,
      isLoading,
      activeNavigator,
      renew,
      signinUnavailable,
      renewalPending,
      holdsRefusals,
      currentAccessToken,
      signedInMomentsAgo,
      names.signInStarted,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextProps {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used inside AuthProvider.");
  }
  return context;
}
