import { act, cleanup } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { renderStage, stage, target } from "./ceremony-stage.test-helpers";

afterEach(() => {
  Reflect.deleteProperty(HTMLElement.prototype, "animate");
});

interface Fade {
  readonly element: HTMLElement;
  readonly away: boolean;
  readonly resolve: () => void;
  readonly reject: () => void;
}

function controlledFades() {
  const fades: Fade[] = [];
  Object.defineProperty(HTMLElement.prototype, "animate", {
    configurable: true,
    value(this: HTMLElement, keyframes: Keyframe[]) {
      const finished = new Promise<void>((resolve, reject) => {
        fades.push({
          element: this,
          away: keyframes.length === 1 && keyframes[0]?.opacity === 0,
          resolve,
          reject: () => reject(new Error("cancelled")),
        });
      });
      return { finished };
    },
  });
  const away = () => fades.filter((fade) => fade.away);
  const fadedAway = () => away().map((fade) => fade.element);
  const end = async (how: "resolve" | "reject") => {
    await act(async () => {
      away().forEach((fade) =>
        how === "resolve" ? fade.resolve() : fade.reject(),
      );
      await Promise.resolve();
    });
  };
  return { fadedAway, end };
}

function hiddenTarget() {
  const ref = target();
  ref.current?.style.setProperty("visibility", "hidden");
  return ref;
}

describe("CeremonyStage on a device that cannot keep up", () => {
  it("fades the stage and its backdrop away rather than cutting to the page", async () => {
    // given
    const { fadedAway, end } = controlledFades();
    const { run, onDone } = renderStage();

    // when
    run(600, 60);

    // then
    const backdrop = document.querySelector("[data-ceremony-backdrop]");
    expect(fadedAway()).toEqual([stage(), backdrop]);
    expect(onDone).not.toHaveBeenCalled();
    await end("resolve");
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("finishes even when the fade is interrupted", async () => {
    // given
    const { end } = controlledFades();
    const { run, onDone } = renderStage();
    run(600, 60);

    // when
    await end("reject");

    // then
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("does not finish for a stage that is already gone", async () => {
    // given
    const { end } = controlledFades();
    const { run, onDone } = renderStage();
    run(600, 60);
    cleanup();

    // when
    await end("resolve");

    // then
    expect(onDone).not.toHaveBeenCalled();
  });

  it("shows the real card under the fading stage", () => {
    // given
    controlledFades();
    const ref = hiddenTarget();
    const { run } = renderStage({ target: ref });

    // when
    run(600, 60);

    // then
    expect(ref.current?.style.visibility).toBe("visible");
  });

  it("keeps a target that is another card hidden while fading in place", () => {
    // given
    controlledFades();
    const ref = hiddenTarget();
    const { run } = renderStage({ target: ref, settle: "fade" });

    // when
    run(600, 60);

    // then
    expect(ref.current?.style.visibility).toBe("hidden");
  });

  it("stops drawing once it has started fading", () => {
    // given
    controlledFades();
    const { run, onFrame } = renderStage();
    run(600, 60);
    const drawn = onFrame.mock.calls.length;

    // when
    run(200);

    // then
    expect(onFrame).toHaveBeenCalledTimes(drawn);
  });
});
