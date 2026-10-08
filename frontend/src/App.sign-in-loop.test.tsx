// A server that refuses every token the identity provider issues, as one configured for another
// audience or issuer does. The identity provider still has a session, so each sign-in comes straight
// back with a fresh token the server refuses again: the whole app, its real session and its real API
// client, with only the network and the navigation to the sign-in stood in for.

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import App from "@/App";
import { SESSION_REFUSED } from "@/auth/SessionRefused";
import { queryClient } from "@/lib/query-client";
import { selfHostedEdition } from "@/selfhosted/edition";

const startLogin = vi.hoisted(() => vi.fn());

vi.mock("@/auth/core/session-client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/auth/core/session-client")>()),
  startLogin,
}));

const tokenOf = (renewal: number) =>
  `h.${btoa(JSON.stringify({ sub: "alice-sub", n: renewal }))}.s`;

/** Every renewal answers 200 with a new token, and every API request answers 401. */
function aServerRefusingEveryToken() {
  let renewals = 0;
  return vi.spyOn(globalThis, "fetch").mockImplementation((input) => {
    const url = input instanceof Request ? input.url : input.toString();
    if (url.endsWith("/auth/refresh")) {
      renewals += 1;
      return Promise.resolve(
        Response.json({
          accessToken: tokenOf(renewals),
          expiresIn: 300,
          idToken: null,
        }),
      );
    }
    return Promise.resolve(
      Response.json(
        { status: 401, title: "Unauthorized", code: "invalid_token" },
        {
          status: 401,
          headers: { "content-type": "application/problem+json" },
        },
      ),
    );
  });
}

/** The first page load: refused, so the guard leaves for a sign-in that comes straight back. */
async function signedInOnceAndRefused(): Promise<void> {
  aServerRefusingEveryToken();
  window.history.pushState({}, "", "/communities");
  const firstLoad = render(<App edition={selfHostedEdition} />);
  await waitFor(() => expect(startLogin).toHaveBeenCalledOnce());
  firstLoad.unmount();
  queryClient.clear();
}

beforeEach(() => {
  queryClient.setDefaultOptions({ queries: { retry: false } });
});

afterEach(() => {
  queryClient.clear();
  sessionStorage.clear();
  vi.restoreAllMocks();
  window.history.pushState({}, "", "/");
});

describe("App, when the server refuses every token a sign-in brings back", () => {
  it("says so after one sign-in rather than signing in again and again", async () => {
    // given
    await signedInOnceAndRefused();

    // when the sign-in comes back to a fresh page load
    render(<App edition={selfHostedEdition} />);

    // then
    expect(await screen.findByText(SESSION_REFUSED)).toBeInTheDocument();
    expect(startLogin).toHaveBeenCalledOnce();
  });

  it("leaves for a sign-in again only when asked to", async () => {
    // given
    await signedInOnceAndRefused();
    render(<App edition={selfHostedEdition} />);
    await screen.findByText(SESSION_REFUSED);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    // then
    expect(startLogin).toHaveBeenCalledTimes(2);
  });
});
