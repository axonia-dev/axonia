import { render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  setStoredActiveOrganizationId,
  storedActiveOrganizationId,
} from "@/auth/active-organization";
import { AuthScopeRecovery } from "@/auth/AuthScopeRecovery";

const mockUseAuth = vi.fn();

vi.mock("@/auth/session", () => ({
  useAuth: () => mockUseAuth() as unknown,
}));

/** A token carrying exactly the claims `activeOrganizationId` reads. */
function tokenWith(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: "none", typ: "JWT" }));
  return `${header}.${btoa(JSON.stringify(payload))}.signature`;
}

function signedInWith(accessToken: string) {
  return {
    isAuthenticated: true,
    user: { access_token: accessToken, profile: { sub: "a-member" } },
  };
}

afterEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.clearAllMocks();
});

describe("AuthScopeRecovery", () => {
  it("forgets an organization the session did not come back holding", () => {
    // given - the edge dropped a dead organization scope and signed the member in without it, which
    // it cannot tell localStorage about
    setStoredActiveOrganizationId("dead-hub");
    mockUseAuth.mockReturnValue(signedInWith(tokenWith({ sub: "a-member" })));

    // when
    render(<AuthScopeRecovery />);

    // then - the stale alias is gone, so the next login cannot fold it back in
    expect(storedActiveOrganizationId()).toBeUndefined();
  });

  it("leaves an organization alone when the token agrees it is the active one", () => {
    // given
    setStoredActiveOrganizationId("live-hub");
    mockUseAuth.mockReturnValue(signedInWith(tokenWith({ org: "live-hub" })));

    // when
    render(<AuthScopeRecovery />);

    // then
    expect(storedActiveOrganizationId()).toBe("live-hub");
  });

  it("leaves an organization alone while the session is still being established", () => {
    // given - before the first renewal answers, an absent org claim means nothing yet
    setStoredActiveOrganizationId("live-hub");
    mockUseAuth.mockReturnValue({ isAuthenticated: false, user: undefined });

    // when
    render(<AuthScopeRecovery />);

    // then
    expect(storedActiveOrganizationId()).toBe("live-hub");
  });

  it("does nothing when there was no organization remembered in the first place", () => {
    // given
    mockUseAuth.mockReturnValue(signedInWith(tokenWith({ sub: "a-member" })));

    // when
    render(<AuthScopeRecovery />);

    // then
    expect(storedActiveOrganizationId()).toBeUndefined();
  });
});
