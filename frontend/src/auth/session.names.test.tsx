// Each app's tabs share a Web Lock and a BroadcastChannel under the app's own names. The session
// core takes whatever names it is given, so only the app can pin which ones those are.

import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";

import { AuthProvider, useAuth } from "@/auth/session";

const ACCESS_TOKEN = `h.${btoa(JSON.stringify({ sub: "a-sub" }))}.s`;

function SignedIn() {
  const auth = useAuth();
  return <p>{auth.isAuthenticated ? "signed in" : "-"}</p>;
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

it("renews under the customer app's own lock and tells its other tabs on its own channel", async () => {
  // given
  const request = vi.fn(
    async (_name: string, renewal: () => Promise<unknown>) => renewal(),
  );
  vi.stubGlobal("navigator", { ...navigator, locks: { request } });
  vi.spyOn(globalThis, "fetch").mockResolvedValue(
    Response.json({ accessToken: ACCESS_TOKEN, expiresIn: 300, idToken: null }),
  );
  const sibling = new BroadcastChannel("axonia.session");
  const heard = vi.fn();
  sibling.onmessage = (event: MessageEvent<unknown>) => {
    heard(event.data);
  };

  // when
  render(
    <AuthProvider>
      <SignedIn />
    </AuthProvider>,
  );

  // then
  await screen.findByText("signed in");
  expect(request).toHaveBeenCalledWith(
    "axonia.session-renewal",
    expect.any(Function),
  );
  await waitFor(() => expect(heard).toHaveBeenCalledWith("renewed"));
  sibling.close();
});
