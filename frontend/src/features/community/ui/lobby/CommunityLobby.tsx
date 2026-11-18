import type { InfiniteData } from "@tanstack/react-query";
import { useCallback, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";

import { Billboard } from "@/components/patterns/billboard/Billboard";
import { LoadFailed } from "@/components/patterns/LoadFailed";
import { SearchField } from "@/components/ui/SearchField";
import { Skeleton } from "@/components/ui/Skeleton";
import {
  useLobby,
  useLobbySearch,
  useShelfSlugs,
} from "@/features/community/api/use-lobby";
import type { Lobby, LobbyItem } from "@/features/community/domain/lobby";
import { LOBBY_COLUMN } from "@/features/community/ui/lobby/lobby-column";
import { LobbyDeparture } from "@/features/community/ui/lobby/lobby-departure";
import {
  LobbyMorphClaims,
  type MorphClaims,
} from "@/features/community/ui/lobby/lobby-morph";
import {
  LobbyBillboardSlide,
  LobbyCreateHero,
} from "@/features/community/ui/lobby/LobbyBillboardSlide";
import {
  CardSkeletons,
  LobbySearchGrid,
  LobbyShelfGrid,
  SEARCH_LIST,
} from "@/features/community/ui/lobby/LobbyGrid";
import { LobbyShelfRow } from "@/features/community/ui/lobby/LobbyShelfRow";
import { LobbyThreshold } from "@/features/community/ui/lobby/LobbyThreshold";
import {
  type LobbyFaces,
  useLobbyFaces,
} from "@/features/community/ui/lobby/use-lobby-faces";
import { failedToLoad } from "@/lib/failed-to-load";
import type { Page } from "@/lib/page";

/** The billboard runs edge to edge of the content area, out of the shell's gutter. */
const BLEED = "-mx-6";

const GRID_SHELVES = new Set(["OTHERS", "EXPLORE"]);

function LobbyBillboard({
  lobby,
  faces,
  canCreate,
  suspended,
  initialSlide,
  onOpenThreshold,
}: Readonly<{
  lobby: Lobby | undefined;
  faces: LobbyFaces;
  canCreate: boolean;
  /** The slide to open on: the one this visit last showed. */
  initialSlide: number;
  /** Held still while the threshold is open over it, so the slide that opened it stays put. */
  suspended: boolean;
  onOpenThreshold: (item: LobbyItem) => void;
}>) {
  if (lobby === undefined) {
    return (
      <div data-testid="billboard-skeleton" className={BLEED}>
        <Skeleton className="h-128 w-full rounded-none md:h-115" />
      </div>
    );
  }
  if (lobby.billboard.length === 0) {
    return (
      <div className={BLEED}>
        <LobbyCreateHero canCreate={canCreate} />
      </div>
    );
  }
  return (
    <Billboard
      label="Featured communities"
      className={BLEED}
      controlsClassName="md:right-[max(2.5rem,calc(50%-36rem))] md:bottom-12"
      suspended={suspended}
      initialIndex={initialSlide}
      itemLabel={({ item }) => item.name}
      items={lobby.billboard.map((item) => ({ id: item.slug, item }))}
      renderSlide={({ item }, active) => (
        <LobbyBillboardSlide
          item={item}
          active={active}
          faces={faces}
          onOpenThreshold={onOpenThreshold}
        />
      )}
    />
  );
}

function LobbyShelves({
  lobby,
  faces,
  onOpenThreshold,
}: Readonly<{
  lobby: Lobby | undefined;
  faces: LobbyFaces;
  onOpenThreshold: (item: LobbyItem) => void;
}>) {
  if (lobby === undefined) return <CardSkeletons count={4} />;
  return (
    <div className="flex flex-col gap-10">
      {lobby.shelves.map((shelf) =>
        GRID_SHELVES.has(shelf.key) ? (
          <LobbyShelfGrid
            key={shelf.key}
            shelf={shelf}
            faces={faces}
            onOpenThreshold={onOpenThreshold}
          />
        ) : (
          <LobbyShelfRow
            key={shelf.key}
            shelf={shelf}
            faces={faces}
            onOpenThreshold={onOpenThreshold}
          />
        ),
      )}
    </div>
  );
}

function isEmpty(lobby: Lobby | undefined): boolean {
  return (
    lobby !== undefined &&
    lobby.billboard.length === 0 &&
    lobby.shelves.length === 0
  );
}

function everyItem(lobby: Lobby | undefined) {
  if (lobby === undefined) return [];
  return [...lobby.billboard, ...lobby.shelves.flatMap((shelf) => shelf.items)];
}

function slugsOf(items: readonly LobbyItem[]): string[] {
  return items.map((item) => item.slug);
}

/** The slide each visit to the lobby last showed, by history entry, so Back returns to it. */
const shownSlides = new Map<string, string>();

function useShownSlide() {
  const { key: entry } = useLocation();
  const [shownSlide, setShownSlide] = useState(
    () => shownSlides.get(entry) ?? null,
  );
  const onShowSlide = useCallback(
    (slug: string) => {
      shownSlides.set(entry, slug);
      setShownSlide(slug);
    },
    [entry],
  );
  return { shownSlide, onShowSlide };
}

/** The slide on show: the one last reported, else the first, as the billboard opens. */
function slideOn(billboard: readonly string[], shown: string | null) {
  const slide = billboard.find((slug) => slug === shown) ?? billboard.at(0);
  return {
    slide: slide ?? null,
    index: Math.max(0, billboard.indexOf(slide ?? "")),
  };
}

function matchSlugs(search: InfiniteData<Page<LobbyItem>> | undefined) {
  return slugsOf(search?.pages.flatMap((page) => page.content) ?? []);
}

/** Which surface wears each community's morph names right now; see `LobbyMorphClaims`. */
function useMorphClaims(
  lobby: Lobby | undefined,
  q: string,
  threshold: LobbyItem | null,
) {
  const { shownSlide, onShowSlide } = useShownSlide();
  const shelves = useShelfSlugs(lobby);
  const search = useLobbySearch(q).data;
  const searching = q.trim() !== "";
  const thresholdSlug = threshold?.slug ?? null;
  const shown = slideOn(slugsOf(lobby?.billboard ?? []), shownSlide);
  const claims = useMemo<MorphClaims>(
    () => ({
      threshold: thresholdSlug,
      slide: searching ? null : shown.slide,
      lists: searching
        ? [[SEARCH_LIST, matchSlugs(search)]]
        : [...shelves.entries()],
    }),
    [thresholdSlug, searching, shown.slide, search, shelves],
  );
  return { claims, onShowSlide, initialSlide: shown.index };
}

/**
 * The organization's lobby: a billboard of featured communities, the search, then the shelves.
 * Typing a search trades the billboard and the shelves for one grid of matches.
 */
export function CommunityLobby({
  canCreate,
}: Readonly<{ canCreate: boolean }>) {
  const [q, setQ] = useState("");
  const [threshold, setThreshold] = useState<LobbyItem | null>(null);
  const lobby = useLobby();
  const faces = useLobbyFaces(everyItem(lobby.data));
  const searching = q.trim() !== "";
  const morph = useMorphClaims(lobby.data, q, threshold);

  if (failedToLoad(lobby)) {
    return (
      <div className={LOBBY_COLUMN}>
        <LoadFailed
          inline
          message="Could not load communities."
          query={lobby}
        />
      </div>
    );
  }

  const empty = isEmpty(lobby.data);

  return (
    <LobbyDeparture>
      <LobbyMorphClaims claims={morph.claims} onShowSlide={morph.onShowSlide}>
        <div className="flex flex-col gap-8">
          {!searching && (
            <LobbyBillboard
              lobby={lobby.data}
              faces={faces}
              canCreate={canCreate}
              suspended={threshold !== null}
              initialSlide={morph.initialSlide}
              onOpenThreshold={setThreshold}
            />
          )}
          <div className={LOBBY_COLUMN}>
            {!empty && (
              <SearchField
                label="Search communities"
                placeholder="Search communities"
                value={q}
                onChange={setQ}
                className="sm:max-w-xs"
              />
            )}
            {searching ? (
              <LobbySearchGrid q={q} onOpenThreshold={setThreshold} />
            ) : (
              <LobbyShelves
                lobby={lobby.data}
                faces={faces}
                onOpenThreshold={setThreshold}
              />
            )}
          </div>
          <LobbyThreshold item={threshold} onClose={() => setThreshold(null)} />
        </div>
      </LobbyMorphClaims>
    </LobbyDeparture>
  );
}
