import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useStuck } from "@/features/events/ui/timeline/use-stuck";

interface Fake {
  options: IntersectionObserverInit | undefined;
  observed: Element[];
  disconnect: ReturnType<typeof vi.fn>;
  report: (top: number, rootTop: number | null) => void;
}

const real = window.IntersectionObserver;
let fakes: Fake[] = [];

beforeEach(() => {
  fakes = [];
  window.IntersectionObserver = class {
    constructor(
      callback: IntersectionObserverCallback,
      options?: IntersectionObserverInit,
    ) {
      const fake: Fake = {
        options,
        observed: [],
        disconnect: vi.fn(),
        report: (top, rootTop) =>
          callback(
            [
              {
                boundingClientRect: { top } as DOMRectReadOnly,
                rootBounds:
                  rootTop === null
                    ? null
                    : ({ top: rootTop } as DOMRectReadOnly),
              } as IntersectionObserverEntry,
            ],
            this as unknown as IntersectionObserver,
          ),
      };
      fakes.push(fake);
      Object.assign(this, {
        observe: (target: Element) => fake.observed.push(target),
        disconnect: fake.disconnect,
      });
    }
  } as unknown as typeof IntersectionObserver;
});

afterEach(() => {
  window.IntersectionObserver = real;
  document.body.innerHTML = "";
});

function sentinelInMain() {
  const main = document.createElement("main");
  const sentinel = document.createElement("div");
  main.append(sentinel);
  document.body.append(main);
  return { main, sentinel };
}

describe("useStuck", () => {
  it("observes the sentinel against main at both thresholds", () => {
    // given
    const { main, sentinel } = sentinelInMain();
    // when
    renderHook(() => useStuck(sentinel));
    // then
    expect(fakes).toHaveLength(1);
    expect(fakes[0]?.options).toEqual({ root: main, threshold: [0, 1] });
    expect(fakes[0]?.observed).toEqual([sentinel]);
  });

  it("observes nothing without a sentinel", () => {
    // when
    const { result } = renderHook(() => useStuck(null));
    // then
    expect(fakes).toHaveLength(0);
    expect(result.current).toBe(false);
  });

  it("is stuck once the sentinel rises above main's top, and unstuck when it comes back", () => {
    // given
    const { sentinel } = sentinelInMain();
    const { result } = renderHook(() => useStuck(sentinel));
    // when
    act(() => fakes[0]?.report(59, 60));
    // then
    expect(result.current).toBe(true);
    // when
    act(() => fakes[0]?.report(60, 60));
    // then
    expect(result.current).toBe(false);
  });

  it("reads a jump far past the edge, and no root bounds, as unstuck only when nothing is above", () => {
    // given
    const { sentinel } = sentinelInMain();
    const { result } = renderHook(() => useStuck(sentinel));
    // when
    act(() => fakes[0]?.report(-4000, 60));
    // then
    expect(result.current).toBe(true);
    // when
    act(() => fakes[0]?.report(-4000, null));
    // then
    expect(result.current).toBe(false);
  });

  it("disconnects on unmount and when the sentinel changes", () => {
    // given
    const first = sentinelInMain().sentinel;
    const second = sentinelInMain().sentinel;
    const { rerender, unmount } = renderHook(({ el }) => useStuck(el), {
      initialProps: { el: first },
    });
    // when
    rerender({ el: second });
    // then
    expect(fakes[0]?.disconnect).toHaveBeenCalledOnce();
    // when
    unmount();
    // then
    expect(fakes[1]?.disconnect).toHaveBeenCalledOnce();
  });
});
