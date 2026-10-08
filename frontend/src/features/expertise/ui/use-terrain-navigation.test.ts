import { act, renderHook } from "@testing-library/react";
import type { PointerEvent } from "react";
import { expect, test } from "vitest";

import { useTerrainNavigation } from "@/features/expertise/ui/use-terrain-navigation";

function pointer(pointerId: number, clientX = 400): PointerEvent<HTMLElement> {
  return {
    pointerId,
    button: 0,
    clientX,
    clientY: 300,
    currentTarget: document.createElement("div"),
  } as unknown as PointerEvent<HTMLElement>;
}

function navigate() {
  return renderHook(() =>
    useTerrainNavigation({
      mode: "turn",
      projection: "globe",
      size: { width: 800, height: 600 },
      zoom: 1,
      zoomBy: () => undefined,
      step: { x: 1, y: 1 },
    }),
  );
}

/**
 * The reading can change under a finger that is still down, by another finger on the picture toggle. Lifting it
 * afterwards is the end of a gesture that was already under way, not a fresh tap on whatever mark it is over.
 */
test("a reading changed under a finger still down does not turn its lift into a tap", () => {
  const { result } = navigate();

  act(() => result.current.handlers.onPointerDown(pointer(1)));
  act(() => result.current.reset("flat"));

  expect(result.current.moved()).toBe(true);
});

test("a reading changed with no finger down leaves the next press a tap", () => {
  const { result } = navigate();

  act(() => result.current.reset("flat"));

  expect(result.current.moved()).toBe(false);
});
