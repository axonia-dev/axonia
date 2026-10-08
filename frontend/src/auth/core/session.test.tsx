import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { TEST_NAMES } from "@/test/session-probe";

import { AuthProvider, useAuth } from "./session";

const renewOnce = vi.fn();
const startLogin = vi.fn();
const endSession = vi.fn();

vi.mock("./session-client", () => ({
  renewOnce: (lockName: string) => renewOnce(lockName) as unknown,
  startLogin: (options?: unknown) => startLogin(options) as unknown,
  endSession: () => endSession() as unknown,
}));

const aSession = (expiresIn = 300) => ({
  user: { access_token: "an-access-token", profile: { sub: "alice-sub" } },
  expiresIn,
});

function stateOf(auth: {
  isLoading: boolean;
  isAuthenticated: boolean;
}): string {
  if (auth.isLoading) return "loading";
  return auth.isAuthenticated ? "in" : "out";
}

/** Renders the context so a test can read and drive it. */
function Probe() {
  const auth = useAuth();
  return (
    <div>
      <span data-testid="state">{stateOf(auth)}</span>
      <span data-testid="sub">{auth.user?.profile.sub ?? "-"}</span>
      <button onClick={() => void auth.signinRedirect({ scope: "openid x" })}>
        sign in
      </button>
      <button
        onClick={() =>
          void auth.signinRedirect({ state: { returnTo: "/members" } })
        }
      >
        sign in to
      </button>
      <button onClick={() => void auth.signinRedirect()}>sign in here</button>

      <button onClick={() => void auth.signoutRedirect()}>sign out</button>
      <button onClick={() => void auth.removeUser()}>drop</button>
      <span data-testid="navigator">{auth.activeNavigator ?? "-"}</span>
    </div>
  );
}

const renderProbe = () =>
  render(
    <AuthProvider names={TEST_NAMES}>
      <Probe />
    </AuthProvider>,
  );

async function signedOut(): Promise<void> {
  renewOnce.mockResolvedValue(null);
  renderProbe();
  await waitFor(() =>
    expect(screen.getByTestId("state")).toHaveTextContent("out"),
  );
}

beforeEach(() => {
  renewOnce.mockResolvedValue(aSession());
});

afterEach(() => {
  sessionStorage.clear();
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe("AuthProvider", () => {
  it("asks the edge whether there is a session, because nothing is stored to read", async () => {
    // when
    renderProbe();

    // then
    await waitFor(() =>
      expect(screen.getByTestId("state")).toHaveTextContent("in"),
    );
    expect(renewOnce).toHaveBeenCalledOnce();
    expect(screen.getByTestId("sub")).toHaveTextContent("alice-sub");
  });

  it("renews under the app's own lock", async () => {
    // when
    renderProbe();

    // then
    await waitFor(() =>
      expect(renewOnce).toHaveBeenCalledWith(TEST_NAMES.lock),
    );
  });

  it("settles signed out when the edge reports no session left to renew", async () => {
    // given
    renewOnce.mockResolvedValue(null);

    // when
    renderProbe();

    // then
    await waitFor(() =>
      expect(screen.getByTestId("state")).toHaveTextContent("out"),
    );
  });

  it("reports loading until the first renewal answers, so no guard redirects over it", () => {
    // given a renewal that has not answered yet
    // Definitely assigned by the executor below, which runs before the Promise constructor returns.
    let settle!: (value: ReturnType<typeof aSession>) => void;
    renewOnce.mockReturnValue(
      new Promise<ReturnType<typeof aSession>>((resolve) => {
        settle = resolve;
      }),
    );

    // when
    renderProbe();

    // then
    expect(screen.getByTestId("state")).toHaveTextContent("loading");
    act(() => {
      settle(aSession());
    });
    expect(screen.getByTestId("state")).toHaveTextContent("in");
  });

  it("renews again before the access token expires", async () => {
    // given
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renderProbe();
    await waitFor(() => expect(renewOnce).toHaveBeenCalledOnce());

    // when - a 300 second token is renewed 30 seconds early
    act(() => {
      vi.advanceTimersByTime(270_000);
    });

    // then
    expect(renewOnce).toHaveBeenCalledTimes(2);
  });

  it("never schedules a renewal so close that a short token spins the timer", async () => {
    // given a token already inside the renewal margin
    vi.useFakeTimers({ shouldAdvanceTime: true });
    renewOnce.mockResolvedValue(aSession(1));
    renderProbe();
    await waitFor(() => expect(renewOnce).toHaveBeenCalledOnce());

    // when - the floor is five seconds, so four is still too early
    act(() => {
      vi.advanceTimersByTime(4_000);
    });

    // then
    expect(renewOnce).toHaveBeenCalledOnce();
  });

  it("renews when a tab comes back, whose timers a sleeping machine never fired", async () => {
    // given
    renderProbe();
    await waitFor(() => expect(renewOnce).toHaveBeenCalledOnce());

    // when
    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    // then
    expect(renewOnce).toHaveBeenCalledTimes(2);
  });

  it("does not renew when a tab is hidden rather than shown", async () => {
    // given
    renderProbe();
    await waitFor(() => expect(renewOnce).toHaveBeenCalledOnce());
    const visibility = vi
      .spyOn(document, "visibilityState", "get")
      .mockReturnValue("hidden");

    // when
    act(() => {
      document.dispatchEvent(new Event("visibilitychange"));
    });

    // then
    expect(renewOnce).toHaveBeenCalledOnce();
    visibility.mockRestore();
  });

  it("starts a login by leaving the page, carrying the scope it was asked for", async () => {
    // given
    await signedOut();

    // when
    act(() => {
      screen.getByRole("button", { name: "sign in" }).click();
    });

    // then
    expect(startLogin).toHaveBeenCalledWith({ scope: "openid x" });
    expect(screen.getByTestId("navigator")).toHaveTextContent("signinRedirect");
  });

  it("starts a login carrying where to come back to", async () => {
    // given
    await signedOut();

    // when
    act(() => {
      screen.getByRole("button", { name: "sign in to" }).click();
    });

    // then
    expect(startLogin).toHaveBeenCalledWith({ returnTo: "/members" });
  });

  it("leaves the destination to the client when the caller named none", async () => {
    // given
    await signedOut();

    // when
    act(() => {
      screen.getByRole("button", { name: "sign in here" }).click();
    });

    // then - an empty options object, not { returnTo: undefined }, which
    // exactOptionalPropertyTypes exists to keep apart
    expect(startLogin).toHaveBeenCalledWith({});
  });

  it("ends the session in both places on sign-out", async () => {
    // given
    renderProbe();
    await waitFor(() =>
      expect(screen.getByTestId("state")).toHaveTextContent("in"),
    );

    // when
    act(() => {
      screen.getByRole("button", { name: "sign out" }).click();
    });

    // then
    expect(endSession).toHaveBeenCalledOnce();
    expect(screen.getByTestId("navigator")).toHaveTextContent(
      "signoutRedirect",
    );
  });

  it("drops this tab's token without touching the cookie, which is what a 401 needs", async () => {
    // given
    renderProbe();
    await waitFor(() =>
      expect(screen.getByTestId("state")).toHaveTextContent("in"),
    );

    // when
    act(() => {
      screen.getByRole("button", { name: "drop" }).click();
    });

    // then - signed out in this tab, and nothing was asked of the edge
    expect(screen.getByTestId("state")).toHaveTextContent("out");
    expect(endSession).not.toHaveBeenCalled();
  });
});

describe("useAuth", () => {
  it("refuses to be read outside the provider rather than reporting a signed-out person", () => {
    // given - an undefined context would read as "not signed in" at every call site, which is a
    // wiring mistake presenting as a product state
    const quiet = vi
      .spyOn(console, "error")
      .mockImplementation(() => undefined);

    // then
    expect(() => render(<Probe />)).toThrow(/AuthProvider/);
    quiet.mockRestore();
  });
});
