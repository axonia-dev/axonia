import { act, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthContextProps } from "@/auth/core/session";
import {
  authState,
  KEPT_NOTICE,
  refuse,
  REFUSED_STATE,
  ShellGuard,
  SIGNING_IN,
} from "@/test/session-guard-shell";
import { inQueryClient } from "@/test/session-probe";

const signIn = vi.fn();

const guarded = (auth: AuthContextProps) => (
  <ShellGuard auth={auth} signIn={signIn}>
    <p>secret</p>
  </ShellGuard>
);

const signedOut = (overrides: Partial<AuthContextProps> = {}) =>
  authState({ user: undefined, isAuthenticated: false, ...overrides });

afterEach(() => {
  vi.resetAllMocks();
});

describe("the session guard, with no session", () => {
  it("leaves for a sign-in once the renewal has reported there is no session left", () => {
    // when
    render(guarded(signedOut()), inQueryClient());

    // then
    expect(signIn).toHaveBeenCalledOnce();
    expect(screen.queryByText("secret")).not.toBeInTheDocument();
    expect(screen.getByText(SIGNING_IN)).toBeInTheDocument();
  });

  it.each([
    ["the first renewal has not answered", { isLoading: true }],
    ["a sign-in is already under way", { activeNavigator: "signinRedirect" }],
    ["the identity provider cannot be reached", { signinUnavailable: true }],
  ] as const)("does not leave while %s", (_, overrides) => {
    // when
    render(guarded(signedOut(overrides)), inQueryClient());

    // then
    expect(signIn).not.toHaveBeenCalled();
  });
});

describe("the session guard, when the backend answers 401", () => {
  it("drops this tab's token, which sends the guard to a sign-in", () => {
    // given
    const auth = authState();
    render(guarded(auth), inQueryClient());

    // when
    act(() => {
      refuse("a-token");
    });

    // then
    expect(auth.removeUser).toHaveBeenCalledOnce();
  });

  it("leaves no handler registered once it unmounts", () => {
    // given
    const auth = authState();
    const { unmount } = render(guarded(auth), inQueryClient());

    // when
    unmount();
    refuse("a-token");

    // then
    expect(auth.removeUser).not.toHaveBeenCalled();
  });
});

describe("the session guard, when the backend refuses the token a sign-in just brought back", () => {
  const justSignedIn = (overrides: Partial<AuthContextProps> = {}) =>
    authState({ signedInMomentsAgo: () => true, ...overrides });

  it("keeps the token and says so in place of the page, rather than signing in again", () => {
    // given
    const auth = justSignedIn();
    render(guarded(auth), inQueryClient());

    // when
    act(() => {
      refuse("a-token");
    });

    // then
    expect(auth.removeUser).not.toHaveBeenCalled();
    expect(signIn).not.toHaveBeenCalled();
    expect(screen.getByText(REFUSED_STATE)).toBeInTheDocument();
    expect(screen.queryByText("secret")).not.toBeInTheDocument();
  });

  it("stays on that state when the session then ends, rather than signing in by itself", () => {
    // given
    const { rerender } = render(guarded(justSignedIn()), inQueryClient());
    act(() => {
      refuse("a-token");
    });

    // when
    rerender(guarded(signedOut({ signedInMomentsAgo: () => true })));

    // then
    expect(signIn).not.toHaveBeenCalled();
    expect(screen.getByText(REFUSED_STATE)).toBeInTheDocument();
  });

  it("reads a 401 for a request that carried no token, before any is held, as no refusal of the session", () => {
    // given the page back from the sign-in, its session still being asked for
    const { rerender } = render(
      guarded(
        justSignedIn({
          user: undefined,
          isAuthenticated: false,
          isLoading: true,
        }),
      ),
      inQueryClient(),
    );

    // when a read mounted beside the guard went out with no token
    act(() => {
      refuse(undefined);
    });
    rerender(guarded(justSignedIn()));

    // then
    expect(screen.queryByText(REFUSED_STATE)).not.toBeInTheDocument();
    expect(screen.getByText("secret")).toBeInTheDocument();
  });

  it("still holds a 401 while the identity provider cannot be reached", () => {
    // given
    const auth = justSignedIn({
      signinUnavailable: true,
      holdsRefusals: () => true,
    });
    render(guarded(auth), inQueryClient());

    // when
    act(() => {
      refuse("a-token");
    });

    // then
    expect(screen.queryByText(REFUSED_STATE)).not.toBeInTheDocument();
    expect(screen.getByText(KEPT_NOTICE)).toBeInTheDocument();
  });

  it("reads a 401 for an older token as stale, not as the server refusing the session", () => {
    // given
    const auth = justSignedIn();
    render(guarded(auth), inQueryClient());

    // when
    act(() => {
      refuse("an-older-token");
    });

    // then
    expect(screen.queryByText(REFUSED_STATE)).not.toBeInTheDocument();
    expect(screen.getByText("secret")).toBeInTheDocument();
  });
});
