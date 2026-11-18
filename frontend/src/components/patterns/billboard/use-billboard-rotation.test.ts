import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useBillboardRotation } from "./use-billboard-rotation";

const originalMatchMedia = window.matchMedia;

function stubReducedMotion(reduced: boolean) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: query.includes("prefers-reduced-motion") && reduced,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList;
}

function advance(ms: number) {
  act(() => {
    vi.advanceTimersByTime(ms);
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  stubReducedMotion(false);
});
afterEach(() => {
  vi.useRealTimers();
  window.matchMedia = originalMatchMedia;
});

describe("useBillboardRotation", () => {
  it("advances every 7000 ms and wraps around", () => {
    // given
    const { result } = renderHook(() => useBillboardRotation(3));

    // when
    advance(6999);
    const beforeInterval = result.current.index;
    advance(1);
    const afterOne = result.current.index;
    advance(7000);
    advance(7000);

    // then
    expect([beforeInterval, afterOne, result.current.index]).toEqual([0, 1, 0]);
  });

  it("waits 12000 ms after a nudge, then one more interval", () => {
    // given
    const { result } = renderHook(() => useBillboardRotation(3));

    // when
    act(() => {
      result.current.nudge();
    });
    advance(12_000);
    advance(6999);
    const stillHeld = result.current.index;
    advance(1);

    // then
    expect([stillHeld, result.current.index]).toEqual([0, 1]);
  });

  it("restarts the resume wait on a second nudge", () => {
    // given
    const { result } = renderHook(() => useBillboardRotation(3));
    act(() => {
      result.current.nudge();
    });
    advance(10_000);

    // when
    act(() => {
      result.current.nudge();
    });
    advance(12_000);
    advance(6999);

    // then
    expect(result.current.index).toBe(0);
  });

  it("stops while held and resumes on release", () => {
    // given
    const { result } = renderHook(() => useBillboardRotation(3));
    act(() => {
      result.current.hold();
    });
    advance(70_000);
    const whileHeld = result.current.index;

    // when
    act(() => {
      result.current.release();
    });
    advance(7000);

    // then
    expect([whileHeld, result.current.index]).toEqual([0, 1]);
  });

  it("stays still while paused and resumes when toggled back", () => {
    // given
    const { result } = renderHook(() => useBillboardRotation(3));
    act(() => {
      result.current.togglePaused();
    });
    advance(70_000);
    const whilePaused = [result.current.index, result.current.paused];

    // when
    act(() => {
      result.current.togglePaused();
    });
    advance(7000);

    // then
    expect(whilePaused).toEqual([0, true]);
    expect(result.current.index).toBe(1);
  });

  it("never advances under reduced motion", () => {
    // given
    stubReducedMotion(true);
    const { result } = renderHook(() => useBillboardRotation(3));

    // when
    advance(70_000);

    // then
    expect(result.current).toMatchObject({
      index: 0,
      rotates: false,
      advancing: false,
    });
  });

  it("never advances a single slide", () => {
    // given
    const { result } = renderHook(() => useBillboardRotation(1));

    // when
    advance(70_000);

    // then
    expect(result.current).toMatchObject({ index: 0, rotates: false });
  });

  it("honours custom timings", () => {
    // given
    const { result } = renderHook(() =>
      useBillboardRotation(3, { intervalMs: 1000, resumeAfterMs: 2000 }),
    );
    act(() => {
      result.current.nudge();
    });

    // when
    advance(2000);
    advance(1000);

    // then
    expect(result.current.index).toBe(1);
  });

  it("steps forward and back with wrap-around", () => {
    // given
    const { result } = renderHook(() => useBillboardRotation(3));

    // when
    act(() => {
      result.current.prev();
    });
    const wrappedBack = result.current.index;
    act(() => {
      result.current.next();
    });

    // then
    expect([wrappedBack, result.current.index]).toEqual([2, 0]);
  });

  it("select jumps straight to an index", () => {
    // given
    const { result } = renderHook(() => useBillboardRotation(3));

    // when
    act(() => {
      result.current.select(2);
    });

    // then
    expect(result.current.index).toBe(2);
  });

  it("stays in range when the list shrinks under the current index", () => {
    // given
    const { result, rerender } = renderHook(
      ({ count }) => useBillboardRotation(count),
      { initialProps: { count: 3 } },
    );
    act(() => {
      result.current.select(2);
    });

    // when
    rerender({ count: 2 });

    // then
    expect(result.current.index).toBe(0);
  });

  it("stays at zero for an empty list", () => {
    // given
    const { result } = renderHook(() => useBillboardRotation(0));

    // when
    act(() => {
      result.current.next();
    });

    // then
    expect(result.current.index).toBe(0);
  });
});
