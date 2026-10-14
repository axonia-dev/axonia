import {
  fadeInCurtain,
  raiseCurtain,
} from "@/components/patterns/ceremony/curtain";
import {
  type CeremonyKind,
  handOffCeremony,
  pendingCeremonySlug,
} from "@/features/community/api/ceremony-handoff";
import { preloadHeroCeremony } from "@/features/community/ui/ceremony/lazy-ceremonies";

/** Set before first paint by the inline script in index.html and app.html. */
const ARRIVAL_CLASS = "ceremony-arrival";

function reducedMotion(): boolean {
  return (
    globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches ?? false
  );
}

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
    !reducedMotion()
  ) {
    raiseCurtain();
    preloadHeroCeremony();
  }
  document.documentElement.classList.remove(ARRIVAL_CLASS);
}

/** Hands off the ceremony and settles once the page is dark, so the community loads unseen. */
export async function departForCeremony(
  kind: CeremonyKind,
  slug: string,
): Promise<void> {
  handOffCeremony(kind, slug);
  preloadHeroCeremony();
  if (!reducedMotion()) {
    await fadeInCurtain();
  }
}
