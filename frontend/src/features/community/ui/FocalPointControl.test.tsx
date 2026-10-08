import { act, fireEvent, render, screen } from "@testing-library/react";
import { useEffect, useRef } from "react";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";

import type { FocalPoint } from "@/features/community/domain/community";
import { FocalPointControl } from "@/features/community/ui/FocalPointControl";

beforeAll(() => {
  Element.prototype.setPointerCapture = vi.fn();
  Element.prototype.releasePointerCapture = vi.fn();
});

afterEach(() => {
  vi.useRealTimers();
});

/**
 * A 1200x900 image rendered at 600x200: cover-scale 0.5, so the hidden vertical overflow is
 * 900*0.5 - 200 = 250px and the horizontal overflow is 1200*0.5 - 600 = 0px exactly - the shape of
 * a real hero banner, which is the point. `object-fit: cover` fits the natural image into the
 * `<img>`'s own rendered box, so the stub sits on the image, not on the frame around it.
 */
function Harness({
  onCommit,
}: Readonly<{ onCommit: (focus: FocalPoint) => void | Promise<unknown> }>) {
  const frameRef = useRef<HTMLDivElement>(null);
  const imageRef = useRef<HTMLImageElement>(null);

  useEffect(() => {
    const image = imageRef.current;
    if (image === null) return;
    Object.defineProperty(image, "clientWidth", {
      value: 600,
      configurable: true,
    });
    Object.defineProperty(image, "clientHeight", {
      value: 200,
      configurable: true,
    });
    Object.defineProperty(image, "naturalWidth", {
      value: 1200,
      configurable: true,
    });
    Object.defineProperty(image, "naturalHeight", {
      value: 900,
      configurable: true,
    });
  }, []);

  return (
    <div ref={frameRef} data-focal-frame style={{ position: "relative" }}>
      <img ref={imageRef} data-focal-image src="https://cdn/h.jpg" alt="" />
      <FocalPointControl
        value={{ x: 50, y: 50 }}
        isDisabled={false}
        onCommit={onCommit}
      />
    </div>
  );
}

describe("FocalPointControl", () => {
  it("commits a new point after a drag", () => {
    const onCommit = vi.fn<(focus: FocalPoint) => void>();
    render(<Harness onCommit={onCommit} />);
    const surface = screen.getByTestId("focal-surface");

    fireEvent.pointerDown(surface, {
      clientX: 100,
      clientY: 100,
      pointerId: 1,
    });
    fireEvent.pointerMove(surface, { clientX: 100, clientY: 60, pointerId: 1 });
    fireEvent.pointerUp(surface, { pointerId: 1 });

    expect(onCommit).toHaveBeenCalledTimes(1);
    // x has zero overflow on this image, so it must stay put rather than divide by zero into NaN.
    expect(onCommit.mock.calls[0]?.[0].x).toBe(50);
    expect(onCommit.mock.calls[0]?.[0].y).toBe(66);
  });

  it("does not commit when the pointer never moved", () => {
    const onCommit = vi.fn<(focus: FocalPoint) => void>();
    render(<Harness onCommit={onCommit} />);
    const surface = screen.getByTestId("focal-surface");

    fireEvent.pointerDown(surface, {
      clientX: 100,
      clientY: 100,
      pointerId: 1,
    });
    fireEvent.pointerUp(surface, { pointerId: 1 });

    expect(onCommit).not.toHaveBeenCalled();
  });

  it("does not commit a stray pointerup with no matching pointerdown", () => {
    // e.g. a press that started disabled and was re-enabled mid-gesture
    const onCommit = vi.fn<(focus: FocalPoint) => void>();
    render(<Harness onCommit={onCommit} />);
    const surface = screen.getByTestId("focal-surface");

    fireEvent.pointerUp(surface, { pointerId: 7 });

    expect(onCommit).not.toHaveBeenCalled();
  });

  it("clamps to the percentage range in both directions", () => {
    const onCommit = vi.fn<(focus: FocalPoint) => void>();
    render(<Harness onCommit={onCommit} />);
    const surface = screen.getByTestId("focal-surface");

    fireEvent.pointerDown(surface, {
      clientX: 100,
      clientY: 100,
      pointerId: 1,
    });
    fireEvent.pointerMove(surface, {
      clientX: 100,
      clientY: -9000,
      pointerId: 1,
    });
    fireEvent.pointerUp(surface, { pointerId: 1 });

    fireEvent.pointerDown(surface, {
      clientX: 100,
      clientY: 100,
      pointerId: 2,
    });
    fireEvent.pointerMove(surface, {
      clientX: 100,
      clientY: 9200,
      pointerId: 2,
    });
    fireEvent.pointerUp(surface, { pointerId: 2 });

    expect(onCommit).toHaveBeenCalledTimes(2);
    expect(onCommit.mock.calls[0]?.[0].y).toBe(100);
    expect(onCommit.mock.calls[1]?.[0].y).toBe(0);
  });

  it("restores the preview when the drag is cancelled", () => {
    const onCommit = vi.fn<(focus: FocalPoint) => void>();
    render(<Harness onCommit={onCommit} />);
    const surface = screen.getByTestId("focal-surface");
    const image = document.querySelector<HTMLImageElement>(
      "img[data-focal-image]",
    );
    if (image === null) throw new Error("image not found");

    fireEvent.pointerDown(surface, {
      clientX: 100,
      clientY: 100,
      pointerId: 1,
    });
    fireEvent.pointerMove(surface, { clientX: 100, clientY: 60, pointerId: 1 });
    expect(image.style.objectPosition).toBe("50% 66%");

    fireEvent.pointerCancel(surface, { pointerId: 1 });

    expect(image.style.objectPosition).toBe("50% 50%");
    expect(onCommit).not.toHaveBeenCalled();
  });

  it("commits once, after a debounce, from a burst of slider changes", () => {
    vi.useFakeTimers();
    const onCommit = vi.fn<(focus: FocalPoint) => void>();
    render(<Harness onCommit={onCommit} />);
    const slider = screen.getByRole("slider", { name: /vertical position/i });

    fireEvent.change(slider, { target: { value: "70" } });
    fireEvent.change(slider, { target: { value: "80" } });

    expect(onCommit).not.toHaveBeenCalled();
    vi.advanceTimersByTime(500);

    expect(onCommit).toHaveBeenCalledTimes(1);
    expect(onCommit.mock.calls[0]?.[0]).toEqual({ x: 50, y: 80 });
  });

  it("reverts both the preview and the slider when the commit rejects", async () => {
    vi.useFakeTimers();
    // A plain function, not a `vi.fn().mockRejectedValue()` spy: that helper attaches its own
    // `.then` for its own bookkeeping, which can make an unhandled-rejection check pass even if
    // the component never actually attaches a `.catch`. Counting real invocations by hand and
    // asserting the concrete revert side effects is what actually proves the `.catch` runs.
    let attempts = 0;
    const onCommit = (): Promise<unknown> => {
      attempts += 1;
      return Promise.reject(new Error("network down"));
    };
    render(<Harness onCommit={onCommit} />);
    const slider = screen.getByRole("slider", { name: /vertical position/i });
    if (!(slider instanceof HTMLInputElement)) {
      throw new Error("slider not found");
    }
    const image = document.querySelector<HTMLImageElement>(
      "img[data-focal-image]",
    );
    if (image === null) throw new Error("image not found");

    fireEvent.change(slider, { target: { value: "80" } });
    expect(image.style.objectPosition).toBe("50% 80%");

    await act(() => vi.advanceTimersByTimeAsync(500));

    expect(attempts).toBe(1);
    expect(image.style.objectPosition).toBe("50% 50%");
    expect(slider.value).toBe("50");
  });
});
