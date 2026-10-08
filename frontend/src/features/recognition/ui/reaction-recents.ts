import { currentUserId } from "@/auth/current-user";

export const RECENT_REACTIONS_LIMIT = 8;

const KEY = "axonia-reaction-recents";

/** Keyed on the member, so a delegated tab and the tab that opened it never trade
 *  recents, and a shared machine never shows one member what another reached for. */
function storageKey(): string | null {
  const id = currentUserId();
  return id === undefined ? null : `${KEY}:${id}`;
}

/** Newest first, no repeats, capped. Pure, so the ordering rule is testable without storage. */
export function withRecent(
  recents: readonly string[],
  emoji: string,
): string[] {
  return [emoji, ...recents.filter((e) => e !== emoji)].slice(
    0,
    RECENT_REACTIONS_LIMIT,
  );
}

/** Anything but an array of strings reads as no recents: this is a convenience list, and a
 *  member whose stored value has been corrupted should get an empty group, never a crash. */
export function parseRecents(raw: string | null): string[] {
  if (raw === null) {
    return [];
  }
  try {
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed)
      ? parsed
          .filter((e): e is string => typeof e === "string")
          .slice(0, RECENT_REACTIONS_LIMIT)
      : [];
  } catch {
    return [];
  }
}

export function storedRecents(): string[] {
  const key = storageKey();
  return key === null ? [] : parseRecents(localStorage.getItem(key));
}

/**
 * Records that this member reached for a standard emoji. Only ever called on a pick: a recent
 * is what somebody chose, and withdrawing one is not a choice to remember.
 *
 * This is a browsing convenience held in one browser, never a count. The product never
 * computes or surfaces any aggregate of reactions, and a local last-used list is
 * neither aggregated nor shown to anybody but the member whose list it is.
 */
export function rememberRecent(emoji: string): void {
  const key = storageKey();
  if (key !== null) {
    localStorage.setItem(
      key,
      JSON.stringify(withRecent(storedRecents(), emoji)),
    );
  }
}
