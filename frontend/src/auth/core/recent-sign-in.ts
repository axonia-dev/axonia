// When this tab last left for a sign-in. Kept in sessionStorage, the way the lobby's auto-enter guard
// is, because it has to survive the round trip to the identity provider and back, and it belongs to
// the tab: another tab's sign-in says nothing about this one.

/**
 * How long after a sign-in this tab started a refusal of the token it brought back means the
 * server refuses the session, rather than that the session ended. An identity provider that still
 * has a session answers a sign-in in a second or two, so a loop comes round well inside it, and a
 * person typing a password who takes longer costs one more round at most.
 */
const RECENT_MS = 60_000;

export function markSignInStarted(key: string): void {
  try {
    window.sessionStorage.setItem(key, String(Date.now()));
  } catch {
    // A blocked sessionStorage costs the loop guard, never the sign-in itself.
  }
}

export function signInStartedRecently(key: string): boolean {
  let elapsed: number;
  try {
    elapsed = Date.now() - Number(window.sessionStorage.getItem(key));
  } catch {
    return false;
  }
  return elapsed >= 0 && elapsed < RECENT_MS;
}
