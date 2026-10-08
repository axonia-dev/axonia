import { render } from "@testing-library/react";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { ContributionOverlay } from "@/features/expertise/ui/ContributionOverlay";
import type { Projection } from "@/features/expertise/ui/globe-projection";
import {
  terrainSpotOf,
  type TerrainView,
} from "@/features/expertise/ui/terrain-camera";
import { buildHeightfield } from "@/features/expertise/ui/terrain-heightfield";
import { type Spot } from "@/features/expertise/ui/terrain-projection";
import type { Surface } from "@/features/expertise/ui/terrain-surface";

const size = { width: 800, height: 500 };

export function mark(
  signalId: number,
  x = 100,
  y = 100,
  sourceType: PlacedContribution["sourceType"] = "SLOT",
): PlacedContribution {
  return {
    signalId,
    tag: "kafka",
    x,
    y,
    slot: 0,
    sourceType,
    userId: "u1",
    occurredAt: 0,
    reach: null,
  };
}

export function scene(
  marks: PlacedContribution[],
  projection: Projection,
  zoom = 1,
): { surface: Surface; view: TerrainView } {
  return {
    surface: { field: buildHeightfield(marks, size), size, projection },
    view: { zoom, size, projection },
  };
}

/**
 * Places each mark in its own cell of a coarse grid across the whole box, so no two targets can disturb each
 * other. Handed in as the spot function, it takes the camera out of the question and leaves the budget as the
 * only thing deciding how many buttons there are.
 */
export function inOwnCell(marks: PlacedContribution[]) {
  // A 30px pitch in both axes, comfortably over the 24px diameter, on a 800x500 box: 26 by 16 cells, which is
  // more than the budget so the budget is what runs out first.
  const pitch = 30;
  const columns = 26;
  const at = new Map(
    marks.map((m, index) => [
      m.signalId,
      {
        left: ((0.5 + (index % columns)) * pitch) / size.width,
        top: ((0.5 + Math.floor(index / columns)) * pitch) / size.height,
      },
    ]),
  );
  return (m: PlacedContribution) => at.get(m.signalId) ?? null;
}

export const labels = new Map([[1, "Scaling Kafka, a talk by Ada Lovelace"]]);

export function draw(
  marks: PlacedContribution[],
  overrides: Partial<{
    projection: Projection;
    spotOf: (mark: PlacedContribution) => Spot | null;
    labels: ReadonlyMap<number, string>;
    selectedId: number | null;
    onSelect: (signalId: number) => void;
  }> = {},
) {
  const projection = overrides.projection ?? "flat";
  const { surface, view } = scene(marks, projection);
  return render(
    <ContributionOverlay
      spotOf={overrides.spotOf ?? terrainSpotOf(surface, view)}
      size={size}
      marks={marks}
      labels={overrides.labels ?? labels}
      selectedId={overrides.selectedId ?? null}
      onSelect={overrides.onSelect ?? (() => {})}
    />,
  );
}
