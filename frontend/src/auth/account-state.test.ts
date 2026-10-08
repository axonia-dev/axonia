import { beforeEach, describe, expect, it, vi } from "vitest";

import { forgetStateOfPreviousAccount, signOut } from "@/auth/account-state";
import {
  autoEnterAttempted,
  markAutoEnterAttempted,
  setStoredActiveOrganizationId,
  storedActiveOrganizationId,
} from "@/auth/active-organization";

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});

describe("signOut", () => {
  it("forgets the organization the account was standing in", () => {
    // given a member who entered a hub
    setStoredActiveOrganizationId("acme");

    // when they sign out
    signOut({ signoutRedirect: vi.fn(() => Promise.resolve()) });

    // then the next account to sign in on this browser does not inherit it: the stored choice
    // rides the startup OIDC scope, so it would ask Keycloak for someone else's organization
    expect(storedActiveOrganizationId()).toBeUndefined();
  });

  it("lets the next account be taken into its own sole organization", () => {
    // given this tab already auto-entered the outgoing member's only organization
    markAutoEnterAttempted();

    // when they sign out
    signOut({ signoutRedirect: vi.fn(() => Promise.resolve()) });

    // then the one-shot guard is spent, not carried over: left set, it suppresses the next
    // member's auto-enter and strands them in the lobby
    expect(autoEnterAttempted()).toBe(false);
  });

  it("ends the Keycloak session too", () => {
    // given
    const signoutRedirect = vi.fn(() => Promise.resolve());

    // when
    signOut({ signoutRedirect });

    // then
    expect(signoutRedirect).toHaveBeenCalledOnce();
  });
});

describe("forgetStateOfPreviousAccount", () => {
  it("drops what the browser remembered for a different account", () => {
    // given a session that ended without anyone pressing sign out - it expired, it was revoked,
    // another tab signed out - leaving one member's organization behind
    forgetStateOfPreviousAccount("sub-1");
    setStoredActiveOrganizationId("acme");
    markAutoEnterAttempted();

    // when somebody else signs in on this browser
    forgetStateOfPreviousAccount("sub-2");

    // then they start from their own membership rather than the last member's
    expect(storedActiveOrganizationId()).toBeUndefined();
    expect(autoEnterAttempted()).toBe(false);
  });

  it("keeps the same account's own choice across reloads", () => {
    // given a member who entered a hub
    forgetStateOfPreviousAccount("sub-1");
    setStoredActiveOrganizationId("acme");

    // when they come back and the guard sees the same subject
    forgetStateOfPreviousAccount("sub-1");

    // then their organization survives, which is the whole point of persisting it
    expect(storedActiveOrganizationId()).toBe("acme");
  });

  it("holds its judgement while nobody is signed in", () => {
    // given a signed-in member's stored choice
    forgetStateOfPreviousAccount("sub-1");
    setStoredActiveOrganizationId("acme");

    // when a render happens with no user yet - every reload starts that way, before the stored
    // session is read back
    forgetStateOfPreviousAccount(undefined);

    // then nothing is forgotten: an absent subject is "not known yet", never "somebody else"
    expect(storedActiveOrganizationId()).toBe("acme");
  });
});
