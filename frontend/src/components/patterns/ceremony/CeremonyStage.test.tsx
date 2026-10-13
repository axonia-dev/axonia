import { render } from "@testing-library/react";
import { StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  manualClock,
  moveTarget,
  renderStage,
  stage,
  target,
} from "./ceremony-stage.test-helpers";
import {
  centredPlacement,
  CeremonyStage,
  SETTLE_SECONDS,
} from "./CeremonyStage";

afterEach(() => {
  moveTarget(new DOMRect(100, 80, 768, 400));
  vi.restoreAllMocks();
});

describe("CeremonyStage", () => {
  it("drives the choreography with the elapsed seconds", () => {
    // given
    const { run, onFrame } = renderStage();

    // when
    run(160);

    // then
    expect(onFrame.mock.lastCall?.[1]).toBeCloseTo(0.16);
  });

  it("holds the choreography at its end while settling", () => {
    // given
    const { run, onFrame } = renderStage();

    // when
    run(1400);

    // then
    expect(onFrame.mock.lastCall?.[1]).toBe(1);
  });

  it("does not finish before it has settled", () => {
    // given
    const { run, onDone } = renderStage();

    // when
    run(1600);

    // then
    expect(onDone).not.toHaveBeenCalled();
  });

  it("finishes once it has played and settled", () => {
    // given
    const { run, onDone } = renderStage();

    // when
    run((1 + SETTLE_SECONDS) * 1000 + 32);

    // then
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("settles onto the target where it is at the end, not where it was", () => {
    // given
    const { run } = renderStage();
    moveTarget(new DOMRect(300, 40, 768, 400));

    // when
    run(1840);

    // then
    const card = document.querySelector<HTMLElement>("[data-ceremony-card]");
    const [x = 0, y = 0] = (card?.style.transform.match(/-?[\d.]+/g) ?? []).map(
      Number,
    );
    expect(x).toBeCloseTo(300, 0);
    expect(y).toBeCloseTo(40, 0);
  });

  it("follows the width of the target while settling", () => {
    // given
    const { run } = renderStage();
    moveTarget(new DOMRect(100, 80, 600, 400));

    // when
    run(1840);

    // then
    const card = document.querySelector<HTMLElement>("[data-ceremony-card]");
    expect(parseFloat(card?.style.width ?? "")).toBeCloseTo(600, 0);
  });

  it("settles for as long as it is told to", () => {
    // given
    const { run, onDone } = renderStage({ settleSeconds: 0.4 });

    // when
    run(1000);
    const early = onDone.mock.calls.length;
    run(500);

    // then
    expect(early).toBe(0);
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("plays one loop and finishes once under StrictMode", () => {
    // given
    const { clock, run } = manualClock();
    const onDone = vi.fn();
    const onFrame = vi.fn();
    render(
      <StrictMode>
        <CeremonyStage
          length={1}
          target={target()}
          settle="morph"
          onFrame={onFrame}
          onDone={onDone}
          clock={clock}
        >
          <p>card</p>
        </CeremonyStage>
      </StrictMode>,
    );

    // when
    run(2000);

    // then
    const times = onFrame.mock.calls
      .map(([, t]) => t as number)
      .filter((t) => t > 0 && t < 1);
    expect(onDone).toHaveBeenCalledOnce();
    expect(new Set(times).size).toBe(times.length);
  });

  it("finishes at once without a target to settle onto", () => {
    // given
    const onDone = vi.fn();

    // when
    render(
      <CeremonyStage
        length={1}
        target={{ current: null }}
        settle="morph"
        onFrame={vi.fn()}
        onDone={onDone}
      >
        <p>card</p>
      </CeremonyStage>,
    );

    // then
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("plays nothing when reduced motion is asked for", () => {
    // given
    vi.spyOn(window, "matchMedia").mockImplementation(
      (query) =>
        ({
          matches: query.includes("reduce"),
          media: query,
          addEventListener: () => {},
          removeEventListener: () => {},
        }) as unknown as MediaQueryList,
    );

    // when
    const { onDone } = renderStage();

    // then
    expect(stage()).toBeNull();
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("hides the stage from assistive technology", () => {
    // when
    renderStage();

    // then
    expect(stage()).toHaveAttribute("aria-hidden", "true");
    expect(document.querySelector("[data-ceremony-card]")).toHaveAttribute(
      "inert",
    );
  });
});

describe("centredPlacement", () => {
  it("fits the card inside the viewport, centred", () => {
    // when
    const actual = centredPlacement(
      { width: 768, height: 400 },
      { width: 1280, height: 800 },
    );

    // then
    expect(actual.scale).toBeCloseTo(1232 / 768);
    expect(actual.x).toBeCloseTo(24);
    expect(actual.y).toBeCloseTo((800 - 400 * (1232 / 768)) / 2);
  });

  it("shrinks a card wider than a phone", () => {
    // when
    const actual = centredPlacement(
      { width: 380, height: 520 },
      { width: 412, height: 892 },
    );

    // then
    expect(actual.scale).toBeCloseTo(364 / 380);
  });
});
