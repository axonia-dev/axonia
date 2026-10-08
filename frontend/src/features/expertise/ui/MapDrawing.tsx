import { lazy, Suspense } from "react";

import type { MapPerson } from "@/features/expertise/domain/expertise";
import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { ContributionOverlay } from "@/features/expertise/ui/ContributionOverlay";
import type { MapHighlight } from "@/features/expertise/ui/map-highlight";
import { MapLabelLayer } from "@/features/expertise/ui/MapLabelLayer";
import type { MapReading } from "@/features/expertise/ui/MapReadingToggle";
import type { PlacedTopic } from "@/features/expertise/ui/topic-graph-layout";
import type { GraphSize } from "@/features/expertise/ui/use-graph-size";
import type { TerrainScene } from "@/features/expertise/ui/use-terrain-scene";

/** three.js is roughly 170KB gzipped, and a reader who only uses the search must never fetch it. The overlay is
 *  not among these: it takes its projection as a plain callback and imports no three.js of its own, so a keyboard
 *  reaches a mark without first waiting for a chunk. */
const TerrainCanvas = lazy(() =>
  import("@/features/expertise/ui/TerrainCanvas").then((m) => ({
    default: m.TerrainCanvas,
  })),
);
const StreamCanvas = lazy(() =>
  import("@/features/expertise/ui/StreamCanvas").then((m) => ({
    default: m.StreamCanvas,
  })),
);

/** Sized like the drawing it stands in for, so the layout does not jump when the chunk lands. */
function SunkenBox({ size }: Readonly<{ size: GraphSize }>) {
  return (
    <div
      className="bg-surface-sunken block"
      style={{ width: size.width, height: size.height }}
    />
  );
}

/** Whichever picture is showing, and the buttons that reach its marks without a pointer. One component, because
 *  the two readings differ only in what draws them: the overlay takes its projection from the scene either way. */
export function MapDrawing({
  picture,
  scene,
  marks,
  topics,
  people,
  faced,
  labels,
  highlight,
  selectedId,
  onSelect,
}: Readonly<{
  picture: MapReading;
  scene: TerrainScene;
  marks: PlacedContribution[];
  topics: PlacedTopic[];
  people: ReadonlyMap<string, MapPerson>;
  faced: ReadonlySet<number>;
  labels: ReadonlyMap<number, string>;
  highlight: MapHighlight;
  selectedId: number | null;
  onSelect: (signalId: number) => void;
}>) {
  return (
    <>
      <Suspense fallback={<SunkenBox size={scene.view.size} />}>
        {picture === "terrain" ? (
          <TerrainCanvas
            surface={scene.surface}
            view={scene.view}
            marks={marks}
            topics={topics}
            people={people}
            faced={faced}
            highlight={highlight}
            chosen={selectedId}
          />
        ) : (
          <StreamCanvas
            marks={marks}
            size={scene.view.size}
            camera={scene.camera}
            highlight={highlight}
            chosen={selectedId}
          />
        )}
      </Suspense>
      <MapLabelLayer labels={scene.labels} highlight={highlight.tags} />
      <ContributionOverlay
        spotOf={scene.spotOf}
        size={scene.view.size}
        marks={marks}
        labels={labels}
        selectedId={selectedId}
        onSelect={onSelect}
      />
    </>
  );
}
