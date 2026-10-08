import { useEffect, useRef, useSyncExternalStore } from "react";
import {
  NavigationType,
  useLocation,
  useNavigationType,
} from "react-router-dom";

import {
  ACCOUNT_SETTINGS,
  ORGANIZATION_SETTINGS,
} from "@/components/patterns/settings-nav";

/**
 * Where the member was before they entered a shell-replacing surface - settings or docs -
 * so leaving returns them to what they were doing rather than to a fixed list.
 *
 * Module-level rather than router `state`, which cannot survive any of the three ways
 * settings is entered and used: the account menu's items are react-aria `MenuItem`s
 * carrying an `href` and nothing else, the legacy `/admin/*` paths arrive by redirect,
 * and the rail's own links would drop any state threaded through them the moment you
 * moved between sections. Deliberately not persisted: a reload has no "what you were
 * doing" to return to, and the caller's fallback is the honest answer there.
 *
 * A store rather than a plain variable because it is written from an effect and read
 * during render: on the legacy-path redirect below, the shell can mount before the
 * effect that invalidates the return point, and would otherwise keep pointing at a path
 * that only leads back into settings.
 */
let lastAppPath: string | null = null;
const listeners = new Set<() => void>();

function setLastAppPath(next: string | null): void {
  if (next === lastAppPath) return;
  lastAppPath = next;
  for (const listener of listeners) listener();
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Label for the way out of a shell-replacing surface (settings, docs): shared so both
 *  surfaces' back links read identically rather than drifting apart as separate strings. */
export const BACK_LABEL = "Back to the app";

/** Matched on the catalogue's own constants so a rename cannot silently start recording
 *  settings paths as somewhere to return to. A community's own `settings/policy` and
 *  `settings/members` are app pages, not this surface, and must keep being recorded.
 *
 *  Docs is here for the same reason: it is a shell-replacing surface with its own way out,
 *  so recording it would make that way out point at the page it is on. */
function isOwnSurface(pathname: string): boolean {
  if (pathname === "/docs" || pathname.startsWith("/docs/")) return true;
  // The consent page leaves the app; coming back to it would replay a spent request.
  if (pathname.startsWith("/oauth/")) return true;
  return [ACCOUNT_SETTINGS, ORGANIZATION_SETTINGS].some((surface) =>
    pathname.includes(`/${surface}`),
  );
}

/** Mounted once, above the authenticated route table: only signed-in destinations are
 *  somewhere to come back to. */
export function useRememberAppLocation(): void {
  const { pathname, search } = useLocation();
  const navigationType = useNavigationType();
  const previous = useRef<string | null>(null);
  useEffect(() => {
    const here = pathname + search;
    // A replace erases the entry it lands on. If that entry was the return point, it is
    // no longer somewhere to go back to: the legacy `/admin/*` paths redirect into
    // settings exactly this way, and returning to one would only redirect back in.
    // Scoped to the entry actually replaced, so settings' own landing redirect - which
    // replaces a settings URL, not the app page before it - leaves the return point alone.
    if (
      navigationType === NavigationType.Replace &&
      previous.current === lastAppPath
    ) {
      setLastAppPath(null);
    }
    if (!isOwnSurface(pathname)) setLastAppPath(here);
    previous.current = here;
  }, [pathname, search, navigationType]);
}

/** The way out of settings or docs: what the member was last doing, or `fallback` for a
 *  session with nothing to go back to - a bookmark, a reload, a legacy path opened cold. */
export function useAppReturnPath(fallback: string): string {
  return useSyncExternalStore(subscribe, () => lastAppPath) ?? fallback;
}

/** Test-only reset: the store outlives a single render tree by design. */
export function forgetAppLocation(): void {
  setLastAppPath(null);
}
