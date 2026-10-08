import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  autoEnterAttempted,
  markAutoEnterAttempted,
  rearmAutoEnterOnOrganizationToken,
  setStoredActiveOrganizationId,
  storedActiveOrganizationId,
} from "@/auth/active-organization";
import { AuthGuard } from "@/auth/AuthGuard";
import { currentUserId } from "@/auth/current-user";
import { getAccessToken, setAccessToken } from "@/auth/token";
import { inQueryClient } from "@/test/session-probe";

const mockUseAuth = vi.fn();

vi.mock("@/auth/session", () => ({
  useAuth: () => mockUseAuth() as unknown,
}));

function authState(overrides: Record<string, unknown>) {
  return {
    isLoading: false,
    isAuthenticated: false,
    activeNavigator: undefined,
    user: undefined,
    signinRedirect: vi.fn(),
    removeUser: vi.fn(),
    holdsRefusals: () => false,
    currentAccessToken: () => undefined,
    signedInMomentsAgo: () => false,
    ...overrides,
  };
}

afterEach(() => {
  setAccessToken(undefined);
  rearmAutoEnterOnOrganizationToken(undefined);
  localStorage.clear();
  sessionStorage.clear();
  vi.clearAllMocks();
});

describe("AuthGuard", () => {
  it("signs in with the startup scope when unauthenticated, since a refresh grant can never add one", () => {
    // given
    setStoredActiveOrganizationId("acme");
    const signinRedirect = vi.fn();
    mockUseAuth.mockReturnValue(authState({ signinRedirect }));

    // when
    render(<AuthGuard>child</AuthGuard>, inQueryClient());

    // then
    expect(signinRedirect).toHaveBeenCalledExactlyOnceWith({
      scope: "openid profile email organization:acme",
    });
    expect(screen.queryByText("child")).not.toBeInTheDocument();
  });

  it("renders children and publishes the token seams when authenticated", () => {
    // given
    mockUseAuth.mockReturnValue(
      authState({
        isAuthenticated: true,
        user: { access_token: "token-123", profile: { sub: "alice-sub" } },
      }),
    );

    // when
    render(<AuthGuard>child</AuthGuard>, inQueryClient());

    // then
    expect(screen.getByText("child")).toBeInTheDocument();
    expect(getAccessToken()).toBe("token-123");
    expect(currentUserId()).toBe("alice-sub");
  });

  it("drops the previous account's state when a different one signs in", () => {
    // given a browser still holding what the last member left behind - their session ended
    // without a sign-out, so nothing has cleared it
    mockUseAuth.mockReturnValue(
      authState({
        isAuthenticated: true,
        user: { access_token: "t", profile: { sub: "alice-sub" } },
      }),
    );
    render(<AuthGuard>child</AuthGuard>, inQueryClient());
    setStoredActiveOrganizationId("acme");
    markAutoEnterAttempted();

    // when somebody else signs in
    mockUseAuth.mockReturnValue(
      authState({
        isAuthenticated: true,
        user: { access_token: "t", profile: { sub: "bob-sub" } },
      }),
    );
    render(<AuthGuard>child</AuthGuard>, inQueryClient());

    // then they start from their own membership: the stored organization rides the startup OIDC
    // scope, and it must not ask Keycloak for one they have nothing to do with
    expect(storedActiveOrganizationId()).toBeUndefined();
  });

  it("keeps the same account's organization across a re-render", () => {
    // given a signed-in member who entered a hub
    mockUseAuth.mockReturnValue(
      authState({
        isAuthenticated: true,
        user: { access_token: "t", profile: { sub: "alice-sub" } },
      }),
    );
    render(<AuthGuard>child</AuthGuard>, inQueryClient());
    setStoredActiveOrganizationId("acme");

    // when the guard renders again for the same subject
    render(<AuthGuard>child</AuthGuard>, inQueryClient());

    // then their choice survives, which is the whole point of persisting it
    expect(storedActiveOrganizationId()).toBe("acme");
  });

  it("re-arms the one-shot organization entry once a token naming an organization arrives", () => {
    // given an org-less session whose entry has fired
    const signedIn = (accessToken: string) =>
      authState({
        isAuthenticated: true,
        user: { access_token: accessToken, profile: { sub: "alice-sub" } },
      });
    mockUseAuth.mockReturnValue(signedIn("orgless-token"));
    render(<AuthGuard>child</AuthGuard>, inQueryClient());
    markAutoEnterAttempted();

    // when the entry's token arrives
    const payload = btoa(JSON.stringify({ sub: "alice-sub", org: "acme" }));
    mockUseAuth.mockReturnValue(signedIn(`h.${payload}.s`));
    render(<AuthGuard>child</AuthGuard>, inQueryClient());

    // then
    expect(autoEnterAttempted()).toBe(false);
  });
});
