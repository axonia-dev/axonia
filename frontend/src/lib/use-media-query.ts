import { useSyncExternalStore } from "react";

/**
 * Subscribe to a CSS media query and re-render when it starts/stops matching.
 * Use for layout forks that cannot both live in the DOM at once (e.g. swapping
 * a wide table for a stacked card list below a breakpoint). For simple
 * show/hide, prefer Tailwind responsive classes instead.
 *
 * Server/initial snapshot is `false`, so SSR and the first client paint assume
 * the query does not match until `matchMedia` is read on mount.
 */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onStoreChange) => {
      const mql = window.matchMedia(query);
      mql.addEventListener("change", onStoreChange);
      return () => mql.removeEventListener("change", onStoreChange);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}
