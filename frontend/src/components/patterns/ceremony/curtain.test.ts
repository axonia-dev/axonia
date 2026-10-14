import { afterEach, describe, expect, it, vi } from "vitest";

import {
  fadeInCurtain,
  liftCurtain,
  raiseCurtain,
  STAGE_DARK,
  takeCurtain,
} from "./curtain";

function curtains(): HTMLElement[] {
  return [...document.querySelectorAll<HTMLElement>("[data-ceremony-curtain]")];
}

afterEach(() => {
  curtains().forEach((curtain) => curtain.remove());
  Reflect.deleteProperty(HTMLElement.prototype, "animate");
  vi.useRealTimers();
});

describe("the ceremony curtain", () => {
  it("covers the whole page in the stage's dark, once", () => {
    // when
    raiseCurtain();
    raiseCurtain();

    // then
    expect(curtains()).toHaveLength(1);
    expect(curtains()[0]?.style).toMatchObject({
      position: "fixed",
      inset: "0px",
      zIndex: "100",
    });
    expect(curtains()[0]?.style.background).toBe("rgb(13, 9, 16)");
    expect(STAGE_DARK).toBe("#0d0910");
  });

  it("lifts by itself when no stage takes it in time", () => {
    // given
    vi.useFakeTimers();
    raiseCurtain(1_000);

    // when
    vi.advanceTimersByTime(1_000);

    // then
    expect(curtains()).toHaveLength(0);
  });

  it("is handed to a stage fully dark while down", () => {
    // given
    raiseCurtain();

    // when
    const taken = takeCurtain();

    // then
    expect(taken).toBe(1);
    expect(curtains()).toHaveLength(0);
  });

  it("is handed over as dark as it still is while lifting", () => {
    // given
    raiseCurtain();
    curtains()[0]?.style.setProperty("opacity", "0.4");

    // when
    const taken = takeCurtain();

    // then
    expect(taken).toBeCloseTo(0.4);
    expect(curtains()).toHaveLength(0);
  });

  it("hands nothing over when none is up", () => {
    // when
    const taken = takeCurtain();

    // then
    expect(taken).toBe(0);
  });

  it("fades out when lifted", () => {
    // given
    const fade = {} as Animation;
    const animate = vi.fn(() => fade);
    HTMLElement.prototype.animate = animate;
    raiseCurtain();

    // when
    liftCurtain();
    fade.onfinish?.call(fade, {} as AnimationPlaybackEvent);

    // then
    expect(animate).toHaveBeenCalledWith(
      [{ opacity: 1 }, { opacity: 0 }],
      expect.objectContaining({ duration: 250 }),
    );
    expect(curtains()).toHaveLength(0);
  });

  it("fades in over the live page and answers once it is fully dark", async () => {
    // given
    let dark = (): void => undefined;
    const fade = {
      finished: new Promise<void>((resolve) => {
        dark = resolve;
      }),
    } as unknown as Animation;
    const animate = vi.fn(() => fade);
    HTMLElement.prototype.animate = animate;
    const settled = vi.fn();

    // when
    void fadeInCurtain().then(settled);
    await Promise.resolve();

    // then
    expect(curtains()).toHaveLength(1);
    expect(animate).toHaveBeenCalledWith(
      [{ opacity: 0 }, { opacity: 1 }],
      expect.objectContaining({ duration: 180 }),
    );
    expect(settled).not.toHaveBeenCalled();
    dark();
    await vi.waitFor(() => expect(settled).toHaveBeenCalledOnce());
  });

  it("answers at once where nothing animates", async () => {
    // when
    await fadeInCurtain();

    // then
    expect(curtains()).toHaveLength(1);
  });

  it("answers even when the fade is cut short", async () => {
    // given
    const fade = {
      finished: Promise.reject(new DOMException("cut", "AbortError")),
    } as unknown as Animation;
    HTMLElement.prototype.animate = vi.fn(() => fade);

    // when
    await fadeInCurtain();

    // then
    expect(curtains()).toHaveLength(1);
  });
});
