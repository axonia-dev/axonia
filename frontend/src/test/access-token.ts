import { afterEach, beforeEach } from "vitest";

import { setAccessToken } from "@/auth/token";

function fakeJwt(claims: Record<string, unknown>): string {
  return `h.${btoa(JSON.stringify(claims))}.s`;
}

/** Registers a token scoped to `organizationId`, the way the auth guard does after sign-in. */
export function signInScopedTo(organizationId: string): void {
  setAccessToken(fakeJwt({ sub: "sub-1", org: organizationId }));
}

/** Registers a token that names no organization: a person who joined nothing yet. */
export function signInOrgless(): void {
  setAccessToken(fakeJwt({ sub: "sub-1" }));
}

export function signOutOfTest(): void {
  setAccessToken(undefined);
}

/** Every test in the calling file runs signed in to `organizationId`. */
export function signInScopedToForEachTest(organizationId: string): void {
  beforeEach(() => signInScopedTo(organizationId));
  afterEach(signOutOfTest);
}
