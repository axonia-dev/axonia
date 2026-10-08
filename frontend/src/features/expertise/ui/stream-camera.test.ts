import * as THREE from "three";
import { describe, expect, it } from "vitest";

import type { PlacedContribution } from "@/features/expertise/ui/contribution-placement";
import { buildStream } from "@/features/expertise/ui/stream-bins";
import {
  binsFor,
  PAD_X,
  reseat,
  RESTING_CAMERA,
  stack,
  type StreamCamera,
  streamFrustum,
  streamSpotOf,
  visibleSpan,
} from "@/features/expertise/ui/stream-layout";

const DAY = 86_400_000;

const size = { width: 800, height: 500 };
const INSET = PAD_X / size.width;

function mark(
  signalId: number,
  occurredAt: number,
  slot = 0,
  tag = "kafka",
): PlacedContribution {
  return {
    signalId,
    tag,
    x: 0,
    y: 0,
    slot,
    sourceType: "SLOT",
    userId: "u-1",
    occurredAt,
    reach: null,
  };
}

function layoutOf(marks: PlacedContribution[]) {
  const layout = buildStream(marks, binsFor(size.width));
  return { layout, stacked: stack(layout, size) };
}

/**
 * The stream's overlay places its buttons through this function while the renderer seats its glyphs through
 * `reseat`, so the two are pinned against each other and against the camera the canvas actually builds. The terrain
 * once had a hand-derived approximation of its own camera, and clicking a mark opened a different contribution.
 */
describe("streamSpotOf", () => {
  /** Three contributions in one neighbourhood under one in another, so the thin band sits clear above the axis and
   *  every seat has a vertical offset the projection has to get the sign of right. */
  const marks = [
    mark(1, 0, 0),
    mark(2, 30 * DAY, 0),
    mark(3, 100 * DAY, 0),
    mark(4, 30 * DAY, 1, "rust"),
  ];

  function seatsOf(all: PlacedContribution[]): PlacedContribution[] {
    const { layout, stacked } = layoutOf(all);
    return reseat(all, layout, stacked, size);
  }

  /** The very camera StreamCanvas builds, through the very frustum it sets on it. */
  function threeSpot(point: THREE.Vector3, at: StreamCamera) {
    const { left, right, top, bottom } = streamFrustum(size, at);
    const camera = new THREE.OrthographicCamera(
      left,
      right,
      top,
      bottom,
      1,
      1000,
    );
    camera.position.set(0, 0, 500);
    camera.updateMatrixWorld(true);
    const ndc = point.clone().project(camera);
    return { left: 0.5 + ndc.x / 2, top: 0.5 - ndc.y / 2 };
  }

  function agreesWith(at: StreamCamera) {
    const seated = seatsOf(marks);
    const spotOf = streamSpotOf(marks, size, at);

    expect(seated.filter((seat) => seat.y !== 0)).not.toHaveLength(0);
    for (const seat of seated) {
      const expected = threeSpot(new THREE.Vector3(seat.x, seat.y, 0), at);
      const actual = spotOf(seat);
      if (actual === null) continue;

      expect(actual.left).toBeCloseTo(expected.left, 6);
      expect(actual.top).toBeCloseTo(expected.top, 6);
    }
  }

  it("agrees with the scene's own camera about where a mark is drawn", () => {
    agreesWith(RESTING_CAMERA);
  });

  it("agrees with it once the reader has zoomed and panned", () => {
    agreesWith({ zoom: 2.5, pan: { x: 120, y: -40 } });
  });

  /** A button left standing over a mark the camera no longer holds takes the click meant for the drawing under it. */
  it("has nowhere to put a mark the camera has left behind", () => {
    const held = (at: StreamCamera) =>
      marks.filter((m) => streamSpotOf(marks, size, at)(m) !== null);

    expect(held(RESTING_CAMERA)).toHaveLength(marks.length);
    expect(held({ zoom: 4, pan: { x: 0, y: 0 } }).length).toBeLessThan(
      marks.length,
    );
  });

  /** `xAt` insets the seat and `boxSpot` projects it back. Were only one of the two to know about the inset, every
   *  button would stand a whole PAD_X clear of the glyph it belongs to. */
  it("puts the oldest mark where the inset starts the history and the newest where it ends", () => {
    const spotOf = streamSpotOf(marks, size, RESTING_CAMERA);

    expect(spotOf(marks[0]!)!.left).toBeCloseTo(INSET, 9);
    expect(spotOf(marks[2]!)!.left).toBeCloseTo(1 - INSET, 9);
  });

  it("carries the inset through to a mark part-way along the history", () => {
    const spotOf = streamSpotOf(marks, size, RESTING_CAMERA);

    expect(spotOf(marks[1]!)!.left).toBeCloseTo(
      INSET + 0.3 * (1 - 2 * INSET),
      9,
    );
  });

  /** The box counts downwards and the world counts upwards, so the band riding on top is the one nearer the top. */
  it("reads the band above the axis as nearer the top of the box", () => {
    const spotOf = streamSpotOf(marks, size, RESTING_CAMERA);
    const above = seatsOf(marks).find((at) => at.slot === 1)!;
    const below = seatsOf(marks).find((at) => at.signalId === 2)!;

    expect(above.y).toBeGreaterThan(0);
    expect(spotOf(above)!.top).toBeLessThan(0.5);
    expect(below.y).toBeLessThan(0);
    expect(spotOf(below)!.top).toBeGreaterThan(0.5);
  });

  it("has nowhere to put a mark the stream does not draw", () => {
    expect(
      streamSpotOf(marks, size, RESTING_CAMERA)(mark(99, Number.NaN)),
    ).toBeNull();
  });

  it("has nowhere to put a mark on a stream with no history", () => {
    expect(streamSpotOf([], size, RESTING_CAMERA)(marks[0]!)).toBeNull();
  });
});

describe("streamFrustum", () => {
  it("spans exactly the box at rest, one world unit per pixel", () => {
    expect(streamFrustum(size, RESTING_CAMERA)).toEqual({
      left: -size.width / 2,
      right: size.width / 2,
      top: size.height / 2,
      bottom: -size.height / 2,
    });
  });

  it("holds half as much of the history at twice the zoom", () => {
    const { left, right } = streamFrustum(size, {
      zoom: 2,
      pan: { x: 0, y: 0 },
    });

    expect(right - left).toBe(size.width / 2);
  });

  /** The pan arrives in the box's axes, where y counts downwards, and the world counts upwards. */
  it("carries the aim across the drawing, downwards in the box", () => {
    const { left, top, bottom } = streamFrustum(size, {
      zoom: 1,
      pan: { x: 100, y: 50 },
    });

    expect(left).toBe(-size.width / 2 + 100);
    expect(top).toBe(size.height / 2 - 50);
    expect(bottom).toBe(-size.height / 2 - 50);
  });
});

/**
 * Zoomed in, an axis that went on naming the first and last contribution would be describing a picture the reader is
 * no longer looking at.
 */
describe("visibleSpan", () => {
  const marks = [mark(1, 0), mark(2, 100 * DAY)];
  const layout = buildStream(marks, binsFor(size.width));

  it("names the whole history at rest", () => {
    expect(visibleSpan(layout, size, RESTING_CAMERA)).toEqual({
      from: layout.from,
      to: layout.to,
    });
  });

  it("names only the slice the camera holds", () => {
    const span = visibleSpan(layout, size, { zoom: 4, pan: { x: 0, y: 0 } });

    expect(span.from).toBeGreaterThan(layout.from);
    expect(span.to).toBeLessThan(layout.to);
    expect(span.to - span.from).toBeLessThan((layout.to - layout.from) / 3);
  });

  it("follows the pan forward through the history", () => {
    const at = { zoom: 4, pan: { x: 0, y: 0 } };
    const middle = visibleSpan(layout, size, at);

    const later = visibleSpan(layout, size, { ...at, pan: { x: 200, y: 0 } });

    expect(later.from).toBeGreaterThan(middle.from);
  });

  /** Panned past the end, the frame reaches into the clearance, where the date is the date of the history's end. */
  it("stops at the ends of the history rather than inventing dates beyond them", () => {
    const span = visibleSpan(layout, size, {
      zoom: 4,
      pan: { x: 10_000, y: 0 },
    });

    expect(span).toEqual({ from: layout.to, to: layout.to });
  });
});
