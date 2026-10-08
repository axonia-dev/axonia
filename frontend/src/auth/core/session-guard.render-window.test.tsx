// A 401 can land in the gap between the render that shows a renewal began and the effects that
// follow it. A sibling rendered after the guard runs its layout effect there: after the guard's own
// and before any passive one.

import { render } from "@testing-library/react";
import { useLayoutEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AuthContextProps } from "@/auth/core/session";
import {
  authState as aSession,
  refuse,
  ShellGuard,
} from "@/test/session-guard-shell";
import { inQueryClient } from "@/test/session-probe";

const mockUseAuth = vi.fn<() => AuthContextProps>();

/** Answers from the state the guard last read, as the session answers from its own. */
function holdsRefusals(): boolean {
  const current = mockUseAuth.mock.results.at(-1)?.value as
    { signinUnavailable?: boolean; renewalPending?: boolean } | undefined;
  return (
    Boolean(current?.signinUnavailable) || Boolean(current?.renewalPending)
  );
}

function authState(overrides: Partial<AuthContextProps>) {
  return aSession({
    user: { access_token: "expired-token", profile: { sub: "alice-sub" } },
    holdsRefusals,
    ...overrides,
  });
}

/** Rendered after the guard, so its layout effect runs after the guard's and before any passive one. */
function RefusedOnceRenewing({ renewalPending }: { renewalPending: boolean }) {
  useLayoutEffect(() => {
    if (renewalPending) refuse("expired-token");
  }, [renewalPending]);
  return null;
}

function RefusedOnceRenewingBesideTheGuard() {
  const auth = mockUseAuth();
  return (
    <>
      <ShellGuard auth={auth} signIn={vi.fn()}>
        <span />
      </ShellGuard>
      <RefusedOnceRenewing renewalPending={auth.renewalPending} />
    </>
  );
}

const refusedOnceRenewing = () => <RefusedOnceRenewingBesideTheGuard />;

afterEach(() => {
  vi.resetAllMocks();
});

describe("the session guard, when a renewal begins", () => {
  it("holds a 401 that lands after the render that began the renewal and before the guard's effects", () => {
    // given
    const before = authState({});
    mockUseAuth.mockReturnValue(before);
    const { rerender } = render(refusedOnceRenewing(), inQueryClient());
    const during = authState({ renewalPending: true });
    mockUseAuth.mockReturnValue(during);

    // when
    rerender(refusedOnceRenewing());

    // then
    expect(before.removeUser).not.toHaveBeenCalled();
    expect(during.removeUser).not.toHaveBeenCalled();
  });
});
