import {
  createContext,
  type ReactNode,
  useContext,
  useEffect,
  useMemo,
} from "react";

import { communityMorphId } from "@/components/patterns/morph/MorphPart";

/** Where a community shows on the lobby: its open threshold, the slide on show, or a card in a list. */
export type MorphSurface =
  | { kind: "threshold" }
  | { kind: "slide" }
  | { kind: "card"; list: string; index: number };

export interface MorphClaims {
  /** The community whose threshold is open. */
  threshold: string | null;
  /** The community on the billboard's slide on show. */
  slide: string | null;
  /** Every list of cards, in render order, with its communities in their order. */
  lists: ReadonlyArray<readonly [string, readonly string[]]>;
}

function ownerOf(claims: MorphClaims, slug: string): string | null {
  if (claims.threshold === slug) return "threshold";
  if (claims.slide === slug) return "slide";
  for (const [list, slugs] of claims.lists) {
    const index = slugs.indexOf(slug);
    if (index !== -1) return `${list}:${index}`;
  }
  return null;
}

function keyOf(surface: MorphSurface): string {
  return surface.kind === "card"
    ? `${surface.list}:${surface.index}`
    : surface.kind;
}

interface LobbyMorphValue {
  owns: (slug: string, surface: MorphSurface) => boolean;
  showSlide: (slug: string) => void;
}

const LobbyMorph = createContext<LobbyMorphValue>({
  owns: () => true,
  showSlide: () => undefined,
});

/**
 * One community, one set of morph names: a view transition aborts on a name worn twice, and the
 * same community can sit on the billboard, on several shelves and in its threshold at once. The
 * open threshold wears them, else the slide on show, else the first card in render order.
 */
export function LobbyMorphClaims({
  claims,
  onShowSlide,
  children,
}: Readonly<{
  claims: MorphClaims;
  onShowSlide: (slug: string) => void;
  children: ReactNode;
}>) {
  const value = useMemo(
    () => ({
      owns: (slug: string, surface: MorphSurface) =>
        ownerOf(claims, slug) === keyOf(surface),
      showSlide: onShowSlide,
    }),
    [claims, onShowSlide],
  );
  return <LobbyMorph value={value}>{children}</LobbyMorph>;
}

/** The morph id this surface wears for the community, or none when another surface wears it. */
export function useLobbyMorphId(
  slug: string,
  surface: MorphSurface | null,
): string | undefined {
  const { owns } = useContext(LobbyMorph);
  if (surface === null || !owns(slug, surface)) return undefined;
  return communityMorphId(slug);
}

/** Tells the lobby which community the billboard shows, so its cards leave it the names. */
export function useShowSlide(slug: string, active: boolean): void {
  const { showSlide } = useContext(LobbyMorph);
  useEffect(() => {
    if (active) showSlide(slug);
  }, [active, slug, showSlide]);
}
