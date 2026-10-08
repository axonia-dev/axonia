import { ZoomIn, ZoomOut } from "lucide-react";
import type { ReactNode } from "react";

import { IconButton } from "@/components/ui/IconButton";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import type { Projection } from "@/features/expertise/ui/globe-projection";
import {
  clampZoom,
  ZOOM_MAX,
  ZOOM_MIN,
  ZOOM_STEP,
} from "@/features/expertise/ui/terrain-aim";

/** The two readings of one set of contributions. The terrain answers what sits next to what, the stream answers when. */
export type MapReading = "terrain" | "stream";

const READINGS = [
  { id: "terrain", label: "Terrain" },
  { id: "stream", label: "Over time" },
];

const PROJECTIONS = [
  { id: "globe", label: "Globe" },
  { id: "flat", label: "Flat" },
];

/** The wheel zooms too, but a wheel is not a keyboard, and the legend's promise that zoom delivers the real icon
 *  has to be reachable without a mouse. */
function ZoomControls({
  zoom,
  onZoom,
}: Readonly<{ zoom: number; onZoom: (next: number) => void }>) {
  return (
    <div className="flex items-center gap-1">
      <IconButton
        label="Zoom out"
        isDisabled={zoom <= ZOOM_MIN}
        onPress={() => onZoom(clampZoom(zoom / ZOOM_STEP))}
      >
        <ZoomOut />
      </IconButton>
      <IconButton
        label="Zoom in"
        isDisabled={zoom >= ZOOM_MAX}
        onPress={() => onZoom(clampZoom(zoom * ZOOM_STEP))}
      >
        <ZoomIn />
      </IconButton>
    </div>
  );
}

/**
 * The map's controls, above the drawing so they never cover it: which picture, and, while the terrain is showing,
 * what shape it is wrapped onto and how close the camera stands. The panel's own Reading select answers a different
 * question and stays where it is.
 */
export function MapReadingToggle({
  reading,
  onReadingChange,
  projection,
  onProjectionChange,
  zoom,
  onZoom,
  search,
}: Readonly<{
  reading: MapReading;
  onReadingChange: (next: MapReading) => void;
  projection: Projection;
  onProjectionChange: (next: Projection) => void;
  zoom: number;
  onZoom: (next: number) => void;
  /** The box that picks things out of the drawing. A control of the map like the rest, and so in the row with
   *  them, but it knows what is drawn and these buttons deliberately do not. */
  search: ReactNode;
}>) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <SegmentedControl
          label="Picture"
          hideLabel
          value={reading}
          options={READINGS}
          onChange={(next) => onReadingChange(next as MapReading)}
        />
        {reading === "terrain" && (
          <SegmentedControl
            label="Projection"
            hideLabel
            value={projection}
            options={PROJECTIONS}
            onChange={(next) => onProjectionChange(next as Projection)}
          />
        )}
        {search}
      </div>
      <div className="flex items-center gap-3">
        {/* The globe hides a hemisphere and the wheel is not labelled, so both are said once, quietly, beside the
            buttons rather than floating over the drawing. Hidden where the row would wrap onto itself. */}
        <p className="text-muted-foreground hidden text-xs lg:block">
          {reading === "terrain" && projection === "globe"
            ? "Drag to turn, wheel to zoom"
            : "Drag to move, wheel to zoom"}
        </p>
        <ZoomControls zoom={zoom} onZoom={onZoom} />
      </div>
    </div>
  );
}
