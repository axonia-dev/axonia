import type { CSSProperties } from "react";
import { useEffect } from "react";

import {
  CENTRE,
  type Community,
  type FocalPoint,
} from "@/features/community/domain/community";
import { accentForeground } from "@/lib/accent";
import { kindHue } from "@/lib/kind-hue";

type Coloured = Pick<Community, "accentColor" | "kindId">;

/**
 * The one colour a community is drawn in. An Organizer who has set an accent gets theirs; one who
 * has not falls back to their kind's hue, which is safe because the accent picker's palette and
 * the kind palette are the same eight colours.
 */
export function communityHue(community: Coloured): string {
  return community.accentColor ?? kindHue(String(community.kindId));
}

/** The theme tokens one hue re-assigns, and nothing else: the accent replaces the product's
 *  primary, it does not repaint success, warning or danger. The focus ring is mixed toward the
 *  foreground, which keeps every palette hue at 3:1 on the surface in both themes. */
function accentProperties(hue: string): Record<string, string> {
  return {
    "--primary": hue,
    "--primary-strong": hue,
    "--primary-foreground": accentForeground(hue),
    "--focus-ring": `color-mix(in srgb, ${hue} 85%, var(--foreground))`,
    "--community-accent": hue,
  };
}

/**
 * The custom properties that carry the community's colour to everything below. Set once, high
 * enough that the whole community space inherits it, rather than per component. This is the form
 * a page drawing several communities at once needs - a list of hero cards, each in its own hue.
 */
export function accentVariables(community: Coloured): CSSProperties {
  return accentProperties(communityHue(community));
}

/**
 * Paints the document in one community's colour for as long as that community *is* the page.
 *
 * An inline style only reaches the React tree below it, and every overlay react-aria opens - the
 * onboarding panel, menus, modals - is portalled onto `document.body`, outside that tree. So the
 * accent is published where the theme itself lives, on the root element, exactly as
 * `ThemeProvider` publishes light and dark. Leaving the community removes the properties again
 * and the product's own primary takes back over.
 */
export function useDocumentAccent(community: Coloured | undefined): void {
  const hue = community === undefined ? null : communityHue(community);

  useEffect(() => {
    if (hue === null) return;
    const { style } = document.documentElement;
    const properties = accentProperties(hue);
    for (const [name, value] of Object.entries(properties)) {
      style.setProperty(name, value);
    }
    return () => {
      for (const name of Object.keys(properties)) style.removeProperty(name);
    };
  }, [hue]);
}

/** Where the image sits inside its frame. Null means the Organizer never chose. */
export function focalPosition(focus: FocalPoint | null | undefined): string {
  const { x, y } = focus ?? CENTRE;
  return `${x}% ${y}%`;
}

/**
 * The banner's last pixel and the identity's first pixel resolve to the same colour on purpose.
 * That single fact is what makes the card read as one object rather than a photo strip stapled to
 * a panel, so the two gradients interpolate this one shared stop rather than each spelling out the
 * same `color-mix` expression, which could drift out of sync with only a comment holding them together.
 */
const SEAM = "color-mix(in srgb, var(--community-accent) 10%, var(--surface))";

/**
 * The percentages are low because the colour driving them is a saturated accent rather than a
 * muted colour sampled from a photograph. Tuned any stronger, the card stops being a card with a
 * photograph on it and starts being a coloured block.
 */
export const BANNER_SCRIM: CSSProperties = {
  backgroundImage:
    `linear-gradient(to top, ${SEAM} 0,` +
    " color-mix(in srgb, var(--community-accent) 17%, transparent) 12%," +
    " transparent 52%)",
};

export const IDENTITY_WASH: CSSProperties = {
  backgroundImage: `linear-gradient(to bottom, ${SEAM} 0, var(--surface) 62%)`,
};

/**
 * Washes the top of the page in the community's accent, then lets it go by 300px so the signal
 * marks arrival without becoming the ground the panels sit on. `backgroundAttachment: "local"` is
 * required because `<main>` is the scroll container: without it the gradient stays pinned to the
 * viewport instead of scrolling away with the content underneath it.
 */
export const MAIN_WASH: CSSProperties = {
  backgroundImage:
    "linear-gradient(to bottom," +
    " color-mix(in srgb, var(--community-accent) 9%, var(--background)) 0," +
    " var(--background) 300px)",
  backgroundAttachment: "local",
};
