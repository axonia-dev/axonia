import { type PointerEvent, useMemo, useRef, useState } from "react";

import { useMapContributions } from "@/features/expertise/api/use-map-contributions";
import type {
  TopicLink,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import {
  authorOf,
  labelsFor,
} from "@/features/expertise/ui/contribution-labels";
import {
  nearestMark,
  reachFor,
} from "@/features/expertise/ui/contribution-picking";
import { placeContributions } from "@/features/expertise/ui/contribution-placement";
import { ContributionStrip } from "@/features/expertise/ui/ContributionStrip";
import { facedSignalIds } from "@/features/expertise/ui/face-gate";
import type { Projection } from "@/features/expertise/ui/globe-projection";
import { withSearch } from "@/features/expertise/ui/map-highlight";
import { searchMap } from "@/features/expertise/ui/map-search";
import { MapDrawing } from "@/features/expertise/ui/MapDrawing";
import {
  type MapReading,
  MapReadingToggle,
} from "@/features/expertise/ui/MapReadingToggle";
import { MapSearchField } from "@/features/expertise/ui/MapSearchField";
import type { DrawnGroup } from "@/features/expertise/ui/named-area-grouping";
import { type Pan } from "@/features/expertise/ui/terrain-aim";
import { panPerPixel } from "@/features/expertise/ui/terrain-camera";
import { markPxFor } from "@/features/expertise/ui/terrain-mark-size";
import { layoutTopics } from "@/features/expertise/ui/topic-graph-layout";
import {
  type GraphSize,
  useGraphSize,
} from "@/features/expertise/ui/use-graph-size";
import {
  holdsTheTouch,
  type NavigationMode,
  useTerrainNavigation,
} from "@/features/expertise/ui/use-terrain-navigation";
import { useTerrainScene } from "@/features/expertise/ui/use-terrain-scene";
import { useTerrainZoom } from "@/features/expertise/ui/use-terrain-zoom";
import { cn } from "@/lib/cn";
import { useDebouncedValue } from "@/lib/use-debounced-value";

/** Long enough that a term is matched once rather than per keystroke: every settled term repaints the ground, which
 *  is a heightfield of tens of thousands of shaded vertices. */
const SEARCH_DEBOUNCE_MS = 180;

/** Turning is the globe's answer to its own far side; every other picture is flat and is carried about instead. */
function modeFor(picture: MapReading, projection: Projection): NavigationMode {
  return picture === "terrain" && projection === "globe" ? "turn" : "pan";
}

/** The stream's camera is orthographic and measured in pixels, so a pixel of the box is one unit over the zoom. */
function stepFor(
  picture: MapReading,
  projection: Projection,
  size: GraphSize | null,
  zoom: number,
): Pan {
  if (size === null) return { x: 1, y: 1 };
  if (picture === "stream") return { x: 1 / zoom, y: 1 / zoom };
  return panPerPixel({ zoom, size, projection });
}

/** Both readings of the one set of contributions, and the camera state they are drawn under. */
export function MapReadings({
  slug,
  topics,
  links,
  groups,
  highlight,
}: Readonly<{
  slug: string;
  topics: TopicNode[];
  links: TopicLink[];
  groups: DrawnGroup[];
  /** The areas the panel beside the map is about, or null when it is about the whole map. */
  highlight: ReadonlySet<string> | null;
}>) {
  const [picture, setPicture] = useState<MapReading>("terrain");
  const [projection, setProjection] = useState<Projection>("globe");
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [term, setTerm] = useState("");
  const settled = useDebouncedValue(term, SEARCH_DEBOUNCE_MS);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const size = useGraphSize(wrapperRef);
  const { zoom, setZoom, zoomBy } = useTerrainZoom(wrapperRef);
  const mode = modeFor(picture, projection);
  const navigation = useTerrainNavigation({
    mode,
    projection,
    size: size ?? { width: 1, height: 1 },
    zoom,
    zoomBy,
    step: stepFor(picture, projection, size, zoom),
  });
  const { data } = useMapContributions(slug, topics.length > 0);

  const items = useMemo(() => data?.items ?? [], [data]);
  const people = useMemo(
    () => new Map((data?.people ?? []).map((p) => [p.userId, p])),
    [data],
  );
  const placedTopics = useMemo(
    () => (size === null ? [] : layoutTopics(topics, links, groups, size)),
    [topics, links, groups, size],
  );
  const marks = useMemo(
    () => placeContributions(items, placedTopics, groups),
    [items, placedTopics, groups],
  );
  const faced = useMemo(() => facedSignalIds(items), [items]);
  // The term is matched here rather than beside the panel because only the map holds the contributions: the panel
  // is about areas, and "who gave the talk about Kafka" is a question about a mark.
  const found = useMemo(
    () => searchMap(settled, { topics, items, people }),
    [settled, topics, items, people],
  );
  // Memoised because the two canvases rebuild what they draw when it moves, and a drag renders this per pointer event.
  const lit = useMemo(() => withSearch(highlight, found), [highlight, found]);
  const labels = useMemo(() => labelsFor(items, people), [items, people]);
  const scene = useTerrainScene({
    marks,
    topics: placedTopics,
    groups,
    size,
    projection,
    zoom,
    spin: navigation.spin,
    pan: navigation.pan,
    picture,
  });

  /** The mark a pointer is over, or null over open ground: the same projection the drawing was painted with. */
  function markUnder(event: PointerEvent<HTMLElement>): number | null {
    if (scene === null || size === null) return null;
    const box = event.currentTarget.getBoundingClientRect();
    const hit = nearestMark({
      marks,
      spotOf: scene.spotOf,
      size,
      at: { x: event.clientX - box.left, y: event.clientY - box.top },
      reach: reachFor(markPxFor(size, zoom)),
    });
    return hit?.signalId ?? null;
  }

  function onPointerDown(event: PointerEvent<HTMLElement>) {
    event.currentTarget.style.cursor = "";
    navigation.handlers.onPointerDown(event);
  }

  function onPointerMove(event: PointerEvent<HTMLElement>) {
    navigation.handlers.onPointerMove(event);
    if (navigation.moved()) return;
    event.currentTarget.style.cursor =
      markUnder(event) === null ? "" : "pointer";
  }

  /**
   * A press that moved the picture is not a click on whatever happens to be under the hand when it stops: ending
   * over a mark must not open it, and ending over open ground must not put the strip away.
   */
  function onPointerUp(event: PointerEvent<HTMLElement>) {
    const dragged = navigation.moved();
    navigation.handlers.onPointerUp(event);
    if (!dragged) setSelectedId(markUnder(event));
  }

  // A mark selected on the globe may be on the far side of the flat reading, and a strip describing something the
  // reader can no longer see is worse than no strip. The camera goes home for the same reason: what a reader
  // turned or carried the frame to is a fact about the picture they just left.
  function choosePicture(next: MapReading) {
    setPicture(next);
    setSelectedId(null);
    setZoom(1);
    navigation.reset(projection);
  }

  function chooseProjection(next: Projection) {
    setProjection(next);
    setSelectedId(null);
    setZoom(1);
    navigation.reset(next);
  }

  const selected = items.find((c) => c.signalId === selectedId) ?? null;

  return (
    <div className="flex flex-col p-3">
      {/* A fixed share of the viewport, and the drawing takes whatever the controls above it leave. Fixed, not
          content-sized: the canvas is sized from this box, so a box that grew with its contents would chase itself.
          Keeping the strip and the cap note OUTSIDE it is what stops a click on a mark from resizing the scene. */}
      <div className="flex h-[60vh] min-h-[440px] flex-col gap-2 md:h-[78vh] md:min-h-[520px]">
        <MapReadingToggle
          reading={picture}
          onReadingChange={choosePicture}
          projection={projection}
          onProjectionChange={chooseProjection}
          zoom={zoom}
          onZoom={setZoom}
          search={
            <MapSearchField term={term} onTermChange={setTerm} found={found} />
          }
        />
        <div
          ref={wrapperRef}
          // A finger on the drawing moves the picture, and a picture that scrolled the page away under it would be
          // unusable on a phone. Where the picture has nowhere to go, a vertical swipe is handed back so a phone can
          // still scroll past a map it is not using. A trackpad sends wheels, which use-terrain-zoom takes.
          className={cn(
            "relative min-h-0 flex-1 cursor-grab active:cursor-grabbing",
            holdsTheTouch(mode, zoom) ? "touch-none" : "touch-pan-y",
          )}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerCancel={navigation.handlers.onPointerCancel}
        >
          {scene !== null && (
            <MapDrawing
              picture={picture}
              scene={scene}
              marks={marks}
              topics={placedTopics}
              people={people}
              faced={faced}
              labels={labels}
              highlight={lit}
              selectedId={selectedId}
              onSelect={setSelectedId}
            />
          )}
        </div>
      </div>
      {data?.truncated === true && (
        <p className="text-muted-foreground mt-2 text-xs">
          Showing the most recent 5,000 contributions. Older activity is not
          drawn. The counts beside the map cover every contribution.
        </p>
      )}
      <ContributionStrip
        contribution={selected}
        author={authorOf(selected, people)}
      />
    </div>
  );
}
