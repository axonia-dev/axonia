// The browser's whole side of the session is one call: AuthProvider mounts, posts to
// /auth/refresh, and is signed in if the edge answers with a token. Nothing is stored, so there is
// no cookie or localStorage to seed - answering that one request is the entire harness.
//
// This keeps the suite hermetic. `vite preview` serves the built bundle as static files and has no
// /auth/* handler at all, so without a stub every spec navigates to /auth/login and fails for a
// reason that has nothing to do with what it is testing.

import type { Page } from "@playwright/test";

import { E2E_USER_ID } from "./api";

const REFRESH = "**/auth/refresh";

/** A syntactically valid JWT carrying the given claims. Nothing in the browser verifies it: the
 *  backend is what validates a token, and the backend is stubbed out here too. */
function jwt(claims: Record<string, unknown>): string {
  const payload = Buffer.from(JSON.stringify(claims)).toString("base64url");
  return `header.${payload}.signature`;
}

/**
 * A signed-in member, scoped to an Organization.
 *
 * The `org` claim matters as much as `sub`: AuthScopeRecovery reads it to decide whether the token
 * is scoped to the Organization the app is standing in, and an org-less token sends the page into
 * a re-authentication redirect rather than into the view under test.
 */
export async function signedIn(
  page: Page,
  claims: Record<string, unknown> = {},
): Promise<void> {
  const accessToken = jwt({
    sub: E2E_USER_ID,
    preferred_username: "dev",
    email: "dev@example.test",
    org: "default",
    exp: 4102444800,
    ...claims,
  });
  await page.route(REFRESH, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ accessToken, expiresIn: 300, idToken: null }),
    }),
  );
}

/**
 * No session at all: with no sealed cookie the edge answers 204 with no body, the ordinary
 * signed-out answer. The app treats it as signed out and renders the public front door.
 */
export async function anonymous(page: Page): Promise<void> {
  await page.route(REFRESH, (route) => route.fulfill({ status: 204 }));
}
