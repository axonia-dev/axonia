import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { type ReactNode, useState } from "react";
import { expect } from "vitest";

import { AuthProvider, type SessionNames, useAuth } from "@/auth/core/session";

/** The names an app gives its session, as a test's own app would. */
export const TEST_NAMES: SessionNames = {
  channel: "an-app.session",
  lock: "an-app.session-renewal",
  signInStarted: "an-app.sign-in-started",
};

/** What `renewOnce` answers when the identity provider could not be reached. */
export const unavailable = (retryAfterSeconds: number | null = null) => ({
  unavailable: true,
  retryAfterSeconds,
});

export const aSession = (expiresIn = 300, accessToken = "an-access-token") => ({
  user: { access_token: accessToken, profile: { sub: "alice-sub" } },
  expiresIn,
});

/**
 * Renders who is signed in, whether sign-in is unavailable, what a silent renewal answered, and,
 * when asked, whether the session holds a 401 and whether this tab left for a sign-in moments ago.
 */
function SessionProbe() {
  const auth = useAuth();
  const [silent, setSilent] = useState("-");
  const [holds, setHolds] = useState("-");
  const [recent, setRecent] = useState("-");
  const state = auth.isAuthenticated ? "in" : "out";
  return (
    <div>
      <span data-testid="state">{auth.isLoading ? "loading" : state}</span>
      <span data-testid="sub">{auth.user?.profile.sub ?? "-"}</span>
      <span data-testid="unavailable">{String(auth.signinUnavailable)}</span>
      <span data-testid="pending">{String(auth.renewalPending)}</span>
      <span data-testid="silent">{silent}</span>
      <button
        onClick={() =>
          void auth
            .signinSilent()
            .then((user) => setSilent(user?.profile.sub ?? "none"))
        }
      >
        renew
      </button>
      <button onClick={() => void auth.removeUser()}>drop</button>
      <span data-testid="holds">{holds}</span>
      <button onClick={() => setHolds(String(auth.holdsRefusals()))}>
        holds
      </button>
      <button onClick={() => void auth.signinRedirect()}>sign in</button>
      <span data-testid="recent">{recent}</span>
      <button onClick={() => setRecent(String(auth.signedInMomentsAgo()))}>
        recent
      </button>
    </div>
  );
}

export function renderSessionProbe(): void {
  render(
    <AuthProvider names={TEST_NAMES}>
      <SessionProbe />
    </AuthProvider>,
  );
}

/** Renders the probe and waits for the first renewal to sign the member in. */
export async function signedIn(): Promise<void> {
  renderSessionProbe();
  await waitFor(() =>
    expect(screen.getByTestId("state")).toHaveTextContent("in"),
  );
}

/** The guard reloads reads refused during an outage, so it renders inside a query client. */
export function inQueryClient(
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } }),
) {
  return {
    wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  };
}
