// Pointer scrubbing and edge rolling, in its own file: TimeRibbon.test.tsx is near the 300-line ceiling.
import { act, fireEvent, render, screen } from "@testing-library/react";
import {
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";

import type { RibbonSpan, RibbonTick } from "@/features/events/domain/ribbon";
import {
  atFraction,
  nearestTick,
  ticksIn,
} from "@/features/events/domain/ribbon";
import { TimeRibbon } from "@/features/events/ui/timeline/TimeRibbon";

const ms = (iso: string) => new Date(iso).getTime();

const SPAN: RibbonSpan = {
  fromMs: ms("2026-01-01T00:00:00Z"),
  toMs: ms("2026-12-31T00:00:00Z"),
};
const TICKS: RibbonTick[] = [
  { at: "2025-12-01T18:00:00Z", kind: "MEMORY" },
  { at: "2026-02-05T18:00:00Z", kind: "MEMORY" },
  { at: "2026-06-25T18:00:00Z", kind: "MEMORY" },
  { at: "2026-09-10T18:00:00Z", kind: "PROJECTED" },
];
const IN_SPAN = ticksIn(TICKS, SPAN);
const TRACK = { left: 0, right: 400, width: 400 } as DOMRect;

beforeAll(() => {
  Element.prototype.setPointerCapture = vi.fn();
  Element.prototype.releasePointerCapture = vi.fn();
});

function setup(extra: Partial<Parameters<typeof TimeRibbon>[0]> = {}) {
  const onScrub = vi.fn();
  const { unmount } = render(
    <TimeRibbon
      span={SPAN}
      bounds={{
        fromMs: ms("2025-06-01T00:00:00Z"),
        toMs: ms("2029-01-01T00:00:00Z"),
      }}
      ticks={TICKS}
      nowMs={ms("2026-07-01T00:00:00Z")}
      lensMs={ms("2026-06-25T18:00:00Z")}
      valueText={(at) => `at ${at}`}
      onScrub={onScrub}
      onSlide={vi.fn()}
      onHold={vi.fn()}
      onBackToToday={vi.fn()}
      {...extra}
    />,
  );
  const slider = screen.getByRole("slider", { name: "Schedule timeline" });
  vi.spyOn(slider, "getBoundingClientRect").mockReturnValue(TRACK);
  return { onScrub, slider, unmount };
}

const at = (tick: RibbonTick | null) => ms(tick?.at ?? "");

describe("TimeRibbon pointer", () => {
  it("scrubs to the nearest session while the pointer is held, and stops on release", () => {
    // given
    const { onScrub, slider } = setup();
    const near = (fraction: number) =>
      at(nearestTick(IN_SPAN, atFraction(fraction, SPAN)));
    // when
    fireEvent.pointerDown(slider, { clientX: 100, pointerId: 1 });
    fireEvent.pointerMove(slider, { clientX: 360, pointerId: 1 });
    fireEvent.pointerUp(slider, { clientX: 360, pointerId: 1 });
    fireEvent.pointerMove(slider, { clientX: 0, pointerId: 1 });
    // then
    expect(onScrub.mock.calls).toEqual([[near(0.25)], [near(0.9)]]);
  });

  it("tells when the pointer takes and lets go of the ribbon", () => {
    // given
    const onHold = vi.fn();
    const { slider } = setup({ onHold });
    // when
    fireEvent.pointerDown(slider, { clientX: 100, pointerId: 1 });
    fireEvent.pointerUp(slider, { clientX: 100, pointerId: 1 });
    // then
    expect(onHold.mock.calls).toEqual([[true], [false]]);
  });

  describe("held on an edge", () => {
    beforeEach(() => {
      vi.useFakeTimers({
        toFake: [
          "requestAnimationFrame",
          "cancelAnimationFrame",
          "performance",
        ],
      });
    });

    afterEach(() => {
      vi.useRealTimers();
    });

    it("rolls the window on toward that edge until let go", () => {
      // given
      const onSlide = vi.fn();
      const { slider } = setup({ onSlide });
      // when the pointer rests on the right edge
      fireEvent.pointerDown(slider, { clientX: 398, pointerId: 1 });
      act(() => {
        vi.advanceTimersByTime(200);
      });
      // then
      expect(onSlide).toHaveBeenCalled();
      expect(onSlide.mock.calls.every(([delta]) => delta > 0)).toBe(true);
      // when
      fireEvent.pointerUp(slider, { clientX: 398, pointerId: 1 });
      onSlide.mockClear();
      act(() => {
        vi.advanceTimersByTime(200);
      });
      // then
      expect(onSlide).not.toHaveBeenCalled();
    });

    it("rolls back on the left edge, and stops once moved off it", () => {
      // given
      const onSlide = vi.fn();
      const { slider } = setup({ onSlide });
      // when
      fireEvent.pointerDown(slider, { clientX: 2, pointerId: 1 });
      act(() => {
        vi.advanceTimersByTime(200);
      });
      // then
      expect(onSlide.mock.calls.every(([delta]) => delta < 0)).toBe(true);
      expect(onSlide).toHaveBeenCalled();
      // when
      fireEvent.pointerMove(slider, { clientX: 200, pointerId: 1 });
      act(() => {
        vi.advanceTimersByTime(50);
      });
      onSlide.mockClear();
      act(() => {
        vi.advanceTimersByTime(200);
      });
      // then
      expect(onSlide).not.toHaveBeenCalled();
    });

    it("stops rolling once the ribbon is taken away under a held pointer", () => {
      // given
      const onSlide = vi.fn();
      const onHold = vi.fn();
      const { slider, unmount } = setup({ onSlide, onHold });
      fireEvent.pointerDown(slider, { clientX: 398, pointerId: 1 });
      // when
      unmount();
      onSlide.mockClear();
      act(() => {
        vi.advanceTimersByTime(200);
      });
      // then
      expect(onSlide).not.toHaveBeenCalled();
      expect(onHold).toHaveBeenLastCalledWith(false);
    });

    it("leaves the window still between the edges", () => {
      // given
      const onSlide = vi.fn();
      const { slider } = setup({ onSlide });
      // when
      fireEvent.pointerDown(slider, { clientX: 200, pointerId: 1 });
      act(() => {
        vi.advanceTimersByTime(200);
      });
      // then
      expect(onSlide).not.toHaveBeenCalled();
    });
  });
});
