import { raiseCurtain } from "@/components/patterns/ceremony/curtain";
import { pendingCeremonySlug } from "@/features/community/api/ceremony-handoff";
import { preloadHeroCeremony } from "@/features/community/ui/ceremony/lazy-ceremonies";

/** Set before first paint by the inline script in index.html and app.html. */
const ARRIVAL_CLASS = "ceremony-arrival";

/** The root lands a hub member on its community; the community is where the ceremony plays. */
function landsOnCeremony(slug: string): boolean {
  const path = globalThis.location.pathname;
  return path === "/" || path === `/communities/${slug}`;
}

/**
 * Run before the app first renders. An act that earns a ceremony and then reloads the app (joining
 * or creating a hub re-authorizes) lands on a page that would otherwise show before its ceremony.
 * The document's own dark first paint hands over to the curtain, which the stage then takes.
 */
export function prepareCeremonyArrival(): void {
  const slug = pendingCeremonySlug();
  const prerendered = document.getElementById("root")?.hasChildNodes() ?? false;
  if (
    slug !== null &&
    landsOnCeremony(slug) &&
    !prerendered &&
    !globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches
  ) {
    raiseCurtain();
    preloadHeroCeremony();
  }
  document.documentElement.classList.remove(ARRIVAL_CLASS);
}
