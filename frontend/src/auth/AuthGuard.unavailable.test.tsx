import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AuthGuard } from "@/auth/AuthGuard";
import { notifyUnauthorized, setAccessToken } from "@/auth/token";
import { toastQueue, ToastViewport } from "@/components/ui/toast";
import { inQueryClient } from "@/test/session-probe";

const mockUseAuth = vi.fn();

vi.mock("@/auth/session", () => ({
  useAuth: () => mockUseAuth() as unknown,
}));

const NOTICE = "Sign-in is unavailable. Retrying.";

/** Answers from the state the guard last read, as the session answers from its own. */
function holdsRefusals(): boolean {
  const current = mockUseAuth.mock.results.at(-1)?.value as
    { signinUnavailable?: boolean; renewalPending?: boolean } | undefined;
  return (
    Boolean(current?.signinUnavailable) || Boolean(current?.renewalPending)
  );
}

function authState(overrides: Record<string, unknown>) {
  const state = {
    isLoading: false,
    isAuthenticated: true,
    activeNavigator: undefined,
    signinUnavailable: true,
    renewalPending: false,
    user: { access_token: "expired-token", profile: { sub: "alice-sub" } },
    signinRedirect: vi.fn(),
    removeUser: vi.fn(),
    ...overrides,
  };
  const user = state.user as { access_token?: string } | undefined;
  return {
    holdsRefusals,
    currentAccessToken: () => user?.access_token,
    signedInMomentsAgo: () => false,
    ...state,
  };
}

const guarded = () => (
  <>
    <ToastViewport />
    <AuthGuard>
      <textarea aria-label="draft" defaultValue="half a post" />
    </AuthGuard>
  </>
);

afterEach(() => {
  setAccessToken(undefined);
  for (const toast of [...toastQueue.visibleToasts]) {
    toastQueue.close(toast.key);
  }
  vi.clearAllMocks();
});

describe("AuthGuard, while the identity provider cannot be reached", () => {
  it("keeps the page and its contents when the backend refuses the expired token", async () => {
    // given
    const auth = authState({});
    mockUseAuth.mockReturnValue(auth);
    render(guarded(), inQueryClient());

    // when
    act(() => {
      notifyUnauthorized("expired-token");
    });

    // then
    expect(auth.removeUser).not.toHaveBeenCalled();
    expect(auth.signinRedirect).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox", { name: "draft" })).toHaveValue(
      "half a post",
    );
    expect(await screen.findByText(NOTICE)).toBeInTheDocument();
  });

  it("says nothing while the held token still works", () => {
    // given
    mockUseAuth.mockReturnValue(authState({}));

    // when
    render(guarded(), inQueryClient());

    // then
    expect(screen.queryByText(NOTICE)).not.toBeInTheDocument();
  });

  it("waits for the scheduled renewal rather than starting a login that cannot complete", () => {
    // given a first visit, with no token to hold
    const auth = authState({ isAuthenticated: false, user: undefined });
    mockUseAuth.mockReturnValue(auth);

    // when
    render(guarded(), inQueryClient());

    // then - the page's own state, not a notice beside a sign-in that is not happening
    expect(auth.signinRedirect).not.toHaveBeenCalled();
    expect(screen.getByRole("status")).toHaveTextContent(NOTICE);
    expect(screen.queryByText("Signing in…")).not.toBeInTheDocument();
    expect(toastQueue.visibleToasts).toHaveLength(0);
  });

  it("offers a sign-in on a first visit, the way out of a wait the provider does not cause", async () => {
    // given
    const auth = authState({ isAuthenticated: false, user: undefined });
    mockUseAuth.mockReturnValue(auth);
    render(guarded(), inQueryClient());

    // when
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    // then
    expect(auth.signinRedirect).toHaveBeenCalledOnce();
  });

  it("offers the same sign-in beside a page it keeps", async () => {
    // given
    const auth = authState({});
    mockUseAuth.mockReturnValue(auth);
    render(guarded(), inQueryClient());
    act(() => {
      notifyUnauthorized("expired-token");
    });

    // when
    await userEvent.click(
      await screen.findByRole("button", { name: "Sign in" }),
    );

    // then
    expect(auth.signinRedirect).toHaveBeenCalledOnce();
  });

  it("clears the notice and keeps the page once a renewal succeeds", async () => {
    // given a 401 held during the outage
    mockUseAuth.mockReturnValue(authState({}));
    const { rerender } = render(guarded(), inQueryClient());
    act(() => {
      notifyUnauthorized("expired-token");
    });
    await screen.findByText(NOTICE);

    // when
    mockUseAuth.mockReturnValue(
      authState({
        signinUnavailable: false,
        user: { access_token: "fresh-token", profile: { sub: "alice-sub" } },
      }),
    );
    rerender(guarded());

    // then
    await waitFor(() =>
      expect(screen.queryByText(NOTICE)).not.toBeInTheDocument(),
    );
    expect(screen.getByRole("textbox", { name: "draft" })).toHaveValue(
      "half a post",
    );
  });

  it("forgets a held 401 once the provider is back, so a later outage says nothing until it matters", async () => {
    // given a 401 held during an outage that has since ended
    mockUseAuth.mockReturnValue(authState({}));
    const { rerender } = render(guarded(), inQueryClient());
    act(() => {
      notifyUnauthorized("expired-token");
    });
    await screen.findByText(NOTICE);
    mockUseAuth.mockReturnValue(authState({ signinUnavailable: false }));
    rerender(guarded());

    await waitFor(() =>
      expect(screen.queryByText(NOTICE)).not.toBeInTheDocument(),
    );

    // when the provider is unreachable again while the fresh token still works
    mockUseAuth.mockReturnValue(authState({}));
    rerender(guarded());

    // then
    await expect(
      screen.findByText(NOTICE, {}, { timeout: 200 }),
    ).rejects.toThrow();
  });

  it("starts a login once the renewal is refused", () => {
    // given
    const auth = authState({
      isAuthenticated: false,
      signinUnavailable: false,
      user: undefined,
    });
    mockUseAuth.mockReturnValue(auth);

    // when
    render(guarded(), inQueryClient());

    // then
    expect(auth.signinRedirect).toHaveBeenCalledOnce();
  });
});
