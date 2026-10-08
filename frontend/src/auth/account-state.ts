import {
  clearAutoEnterFlag,
  clearStoredActiveOrganizationId,
} from "@/auth/active-organization";

/**
 * What this browser remembers about whoever is signed in, and when to throw it away.
 *
 * The active Organization persists in localStorage because it rides the startup OIDC scope, and
 * the lobby's auto-enter guard persists in sessionStorage because it has to survive an authorize
 * round-trip. Both belong to one account. Left behind when that account goes, they are read back
 * as if they belonged to the next one: the stale Organization is requested at the authorize
 * endpoint on someone else's behalf, and a spent auto-enter guard suppresses the new member's own
 * entry and strands them in the lobby.
 */

/** Whose state this browser is holding, so a different account signing in can be recognised. */
const ACCOUNT_STORAGE_KEY = "axonia.account";

function forgetAccountState(): void {
  clearStoredActiveOrganizationId();
  clearAutoEnterFlag();
}

function accountOnRecord(): string | undefined {
  try {
    return window.localStorage.getItem(ACCOUNT_STORAGE_KEY) ?? undefined;
  } catch {
    return undefined;
  }
}

function recordAccount(userId: string): void {
  try {
    window.localStorage.setItem(ACCOUNT_STORAGE_KEY, userId);
  } catch {
    // A blocked localStorage costs the guard, not the sign-out path that does the same job.
  }
}

/** Ends the session and leaves nothing of it behind for the next account to inherit. */
export function signOut(auth: { signoutRedirect: () => Promise<void> }): void {
  forgetAccountState();
  void auth.signoutRedirect();
}

/**
 * Drops the previous account's state when a different one signs in. Signing out is where this
 * normally happens, but a session can end without it - it expires, an administrator revokes it,
 * another tab signs out - and the account that signs in next must not inherit an Organization it
 * has nothing to do with.
 *
 * An absent subject means "not known yet", never "somebody else": every reload renders that way
 * before the stored session is read back.
 */
export function forgetStateOfPreviousAccount(userId: string | undefined): void {
  if (userId === undefined) return;
  if (accountOnRecord() === userId) return;
  forgetAccountState();
  recordAccount(userId);
}
