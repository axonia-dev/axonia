import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import * as THREE from "three";
import { describe, expect, it, vi } from "vitest";

import {
  draw,
  inOwnCell,
  labels,
  mark,
  scene,
} from "@/features/expertise/ui/contribution-overlay-fixture";
import { MAX_OVERLAY_BUTTONS } from "@/features/expertise/ui/ContributionOverlay";
import {
  cameraEye,
  FAR,
  FOV,
  MARK_LIFT,
  NEAR,
  type TerrainView,
} from "@/features/expertise/ui/terrain-camera";
import { surfacePoint } from "@/features/expertise/ui/terrain-surface";

describe("ContributionOverlay", () => {
  it("gives every mark a real button a keyboard can reach", () => {
    draw([mark(1)]);

    expect(
      screen.getByRole("button", {
        name: /Scaling Kafka, a talk by Ada Lovelace/,
      }),
    ).toBeInTheDocument();
  });

  it("selects the contribution it names", async () => {
    const onSelect = vi.fn();
    draw([mark(1)], { onSelect });

    await userEvent.click(screen.getByRole("button"));

    expect(onSelect).toHaveBeenCalledWith(1);
  });

  it("marks the selected one as current", () => {
    draw([mark(1)], { selectedId: 1 });

    expect(screen.getByRole("button")).toHaveAttribute("aria-current", "true");
  });

  /** The cue itself is the mark: the drawing paints the chosen one in the map's chosen ink, which is a cue a
   *  crowded area cannot lose. See `mark-sprites.ts` and the two canvases. */
  it("leaves the chosen mark looking like every other target", () => {
    draw([mark(1), mark(2, 300, 200)], { selectedId: 1 });
    const [chosen, other] = screen.getAllByRole("button");

    expect(chosen!.className).toBe(other!.className);
  });

  it("draws no button for a mark on the far side of the globe", () => {
    draw([mark(1, 0, 250), mark(2, 400, 250)], {
      projection: "globe",
      labels: new Map([...labels, [2, "Hidden"]]),
    });

    expect(screen.getAllByRole("button")).toHaveLength(1);
  });

  it("names a mark by its area when no label was resolved", () => {
    draw([mark(9)], { labels: new Map() });

    expect(screen.getByRole("button", { name: /kafka/ })).toBeInTheDocument();
  });

  /** The overlay serves both readings, so it may know nothing about a surface, a camera or a projection. */
  it("places a button wherever the spot function puts it, whatever drew the mark", () => {
    draw([mark(1)], {
      spotOf: () => ({ left: 0.25, top: 0.75 }),
      labels: new Map(),
    });

    const actual = screen.getByRole("button");

    expect(actual.style.left).toBe("25%");
    expect(actual.style.top).toBe("75%");
  });

  it("draws no button for a mark the reading has nowhere to put", () => {
    draw([mark(1)], { spotOf: () => null, labels: new Map() });

    expect(screen.queryByRole("button")).toBeNull();
  });

  it("caps how many targets it draws", () => {
    const many = Array.from({ length: MAX_OVERLAY_BUTTONS + 20 }, (_, index) =>
      mark(index + 1),
    );

    draw(many, { labels: new Map(), spotOf: inOwnCell(many) });

    expect(screen.getAllByRole("button")).toHaveLength(MAX_OVERLAY_BUTTONS);
  });

  it("draws no target a neighbouring target would cover", () => {
    // Every one of these sits inside one 24px circle. Drawn, they would be one button with four hundred
    // invisible siblings on top of it, and a pointer aimed at any of them would get whichever was last.
    const heap = Array.from({ length: 400 }, (_, index) =>
      mark(index + 1, 300 + (index % 5), 200 + (index % 3)),
    );

    draw(heap, { labels: new Map() });

    expect(screen.getAllByRole("button")).toHaveLength(1);
  });
});

/**
 * The budget used to be spent by reach descending, and only a slot has a reach: on a community with hundreds of
 * talks an artifact was never reachable at all, and the tab order was a ranking of people by audience size.
 */
describe("what the budget keeps", () => {
  const busy = [
    ...Array.from({ length: 500 }, (_, index) => mark(index + 1)),
    mark(9001, 0, 0, "ARTIFACT"),
    mark(9002, 0, 0, "ARTIFACT"),
    mark(9003, 0, 0, "ARTIFACT"),
  ];

  const busyLabels = new Map(busy.map((m) => [m.signalId, `s${m.signalId}`]));
  const busySpots = inOwnCell(busy);

  it("keeps every artifact reachable under a flood of talks", () => {
    draw(busy, { labels: busyLabels, spotOf: busySpots });

    for (const id of [9001, 9002, 9003]) {
      expect(
        screen.getByRole("button", { name: `s${id}` }),
      ).toBeInTheDocument();
    }
  });

  it("spends the budget across all four kinds rather than on one", () => {
    draw(busy, { labels: busyLabels, spotOf: busySpots });

    expect(screen.getAllByRole("button")).toHaveLength(MAX_OVERLAY_BUTTONS);
  });

  it("orders the tab stops by id, never by how many people a talk reached", () => {
    const ranked = [
      { ...mark(1, 100, 100), reach: 10 },
      { ...mark(2, 200, 100), reach: 90 },
      { ...mark(3, 300, 100), reach: 50 },
    ];

    draw(ranked, {
      labels: new Map(ranked.map((m) => [m.signalId, `s${m.signalId}`])),
    });

    expect(
      screen.getAllByRole("button").map((button) => button.textContent),
    ).toEqual(["s1", "s2", "s3"]);
  });
});

/**
 * The regression the old arrangement could not have had: the overlay placed its buttons with a hand-derived
 * orthographic approximation while the renderer drew the marks with a tilted perspective camera, so a click landed
 * on a different contribution than the one under the cursor.
 */
describe("where a button sits", () => {
  function threeSpot(point: THREE.Vector3, view: TerrainView) {
    const camera = new THREE.PerspectiveCamera(
      FOV,
      view.size.width / view.size.height,
      NEAR,
      FAR,
    );
    const eye = cameraEye(view);
    camera.position.set(eye.x, eye.y, eye.z);
    camera.lookAt(0, 0, 0);
    camera.updateMatrixWorld(true);
    const ndc = point.clone().project(camera);
    return { left: 0.5 + ndc.x / 2, top: 0.5 - ndc.y / 2 };
  }

  const grid = [100, 300, 500, 700].flatMap((x, column) =>
    [80, 250, 420].map((y, row) => mark(column * 10 + row + 1, x, y)),
  );
  const gridLabels = new Map(grid.map((m) => [m.signalId, `s${m.signalId}`]));

  // The globe shows one hemisphere by construction, so fewer of the same grid survive there.
  it.each([
    ["flat", 10],
    ["globe", 4],
  ] as const)(
    "agrees with the scene's own camera on the %s reading",
    (projection, atLeast) => {
      const { surface, view } = scene(grid, projection);
      draw(grid, { projection, labels: gridLabels });

      const buttons = screen.getAllByRole("button");
      expect(buttons.length).toBeGreaterThanOrEqual(atLeast);

      for (const button of buttons) {
        const found = grid.find((m) => `s${m.signalId}` === button.textContent);
        const at = surfacePoint(surface, found!.x, found!.y, MARK_LIFT);
        const expected = threeSpot(new THREE.Vector3(at.x, at.y, at.z), view);

        expect(Number.parseFloat(button.style.left) / 100).toBeCloseTo(
          expected.left,
          6,
        );
        expect(Number.parseFloat(button.style.top) / 100).toBeCloseTo(
          expected.top,
          6,
        );
      }
    },
  );

  it("drops a mark the camera cannot see rather than pinning it to an edge", () => {
    const off = [mark(1, 100, 100), mark(2, 20_000, 100)];

    draw(off, {
      labels: new Map(off.map((m) => [m.signalId, `s${m.signalId}`])),
    });

    expect(
      screen.getAllByRole("button").map((button) => button.textContent),
    ).toEqual(["s1"]);
  });
});
