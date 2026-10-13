import { act, render } from "@testing-library/react";
import type { RefObject } from "react";
import { vi } from "vitest";

import { CeremonyStage, type CeremonyStageProps } from "./CeremonyStage";
import type { CeremonyClock } from "./clock";

export function manualClock() {
  let now = 0;
  let pending: (() => void) | null = null;
  const clock: CeremonyClock = {
    now: () => now,
    request: (callback) => {
      pending = callback;
      return 1;
    },
    cancel: () => {
      pending = null;
    },
  };
  const run = (milliseconds: number, frame = 16) => {
    for (let elapsed = 0; elapsed < milliseconds; elapsed += frame) {
      now += frame;
      const callback = pending;
      pending = null;
      act(() => callback?.());
    }
  };
  return { clock, run };
}

let targetBox = new DOMRect(100, 80, 768, 400);

export function moveTarget(box: DOMRect): void {
  targetBox = box;
}

export function target(): RefObject<HTMLElement | null> {
  const element = document.createElement("div");
  element.getBoundingClientRect = () => targetBox;
  return { current: element };
}

export function renderStage(overrides: Partial<CeremonyStageProps> = {}) {
  const { clock, run } = manualClock();
  const onDone = vi.fn();
  const onFrame = vi.fn();
  render(
    <CeremonyStage
      length={1}
      target={target()}
      settle="morph"
      onFrame={onFrame}
      onDone={onDone}
      clock={clock}
      {...overrides}
    >
      <p>card</p>
    </CeremonyStage>,
  );
  return { run, onDone, onFrame };
}

export function stage(): HTMLElement | null {
  return document.querySelector("[data-ceremony-stage]");
}

afterEach(() => {
  targetBox = new DOMRect(100, 80, 768, 400);
  vi.restoreAllMocks();
});
