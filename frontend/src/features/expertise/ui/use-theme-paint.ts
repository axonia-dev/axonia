import { useSyncExternalStore } from "react";

/**
 * What the page is painted in, as a value that changes when the theme or the community's accent does.
 *
 * WebGL reads no cascade: every colour the drawing uses is resolved out of it once, when the layer is built. So a
 * reader who switches theme keeps the old map until something else happens to rebuild it, which in practice meant
 * reloading the page. The theme is applied by toggling a class on the document element and a community publishes
 * its accent as a custom property on that same element, so both are watched here - not the providers, so a canvas
 * rendered on its own still follows the page.
 *
 * The accent is not merely a second theme: effects run child first, so the map builds its layers before the shell
 * above it has published anything, and the first frame of every community map is drawn in the product's own colour.
 * This is what carries the accent onto it. The snapshot is the two values rather than the attribute, so the other
 * writers of the root element's style - a modal taking the scroll, say - cost nothing but a comparison.
 */
function subscribe(onChange: () => void): () => void {
  const observer = new MutationObserver(onChange);
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class", "style"],
  });
  return () => observer.disconnect();
}

/** The accent a community publishes over the product's own while it is the page. See `community-accent.ts`. */
const ACCENT = "--primary";

function painted(): string {
  const root = document.documentElement;
  return `${root.className} ${root.style.getPropertyValue(ACCENT)}`;
}

export function useThemePaint(): string {
  return useSyncExternalStore(subscribe, painted, () => "");
}
