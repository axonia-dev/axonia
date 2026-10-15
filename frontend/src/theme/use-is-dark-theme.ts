import { useSyncExternalStore } from "react";

const DARK = "theme-dark";

function subscribe(onChange: () => void): () => void {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class"],
  });
  return () => observer.disconnect();
}

const isDark = () => document.documentElement.classList.contains(DARK);

/** The painted theme, read from the document rather than the provider, so a component rendered
 *  outside the ThemeProvider (a test, a portal) still follows it. */
export function useIsDarkTheme(): boolean {
  return useSyncExternalStore(subscribe, isDark, () => false);
}
