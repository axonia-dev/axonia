import { cleanup } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { renderStage, target } from "./ceremony-stage.test-helpers";

afterEach(() => {
  vi.restoreAllMocks();
  Reflect.deleteProperty(HTMLElement.prototype, "animate");
});

function card(): HTMLElement {
  return document.querySelector("[data-ceremony-card]") as HTMLElement;
}

function backdrops(): HTMLElement[] {
  return [
    ...document.querySelectorAll<HTMLElement>("[data-ceremony-backdrop]"),
  ];
}

function hiddenTarget() {
  const ref = target();
  ref.current?.style.setProperty("visibility", "hidden");
  return ref;
}

describe("CeremonyStage settling", () => {
  it("wears the dark theme whatever the page's theme", () => {
    // when
    renderStage();

    // then
    expect(card()).toHaveClass("theme-dark");
  });

  it("brings its own backdrop and fades it in rather than cutting to it", () => {
    // given
    const animate = vi.fn();
    Object.defineProperty(HTMLElement.prototype, "animate", {
      value: animate,
      configurable: true,
    });

    // when
    renderStage();

    // then
    expect(backdrops()).toHaveLength(1);
    expect(animate).toHaveBeenCalledWith([{ opacity: 0 }, { opacity: 1 }], {
      duration: 180,
      easing: "ease-out",
    });
  });

  it("clears its backdrop as it settles", () => {
    // given
    const { run } = renderStage();

    // when
    run(1000 + 848);

    // then
    expect(Number(backdrops()[0]?.style.opacity)).toBeLessThan(0.01);
  });

  it("centres the staged card by its own size, not the target's", () => {
    // given
    vi.spyOn(HTMLElement.prototype, "offsetWidth", "get").mockReturnValue(768);
    vi.spyOn(HTMLElement.prototype, "offsetHeight", "get").mockReturnValue(200);

    // when
    renderStage();

    // then
    const scale = (window.innerWidth - 48) / 768;
    expect(card().style.transform).toBe(
      `translate(24px, ${(window.innerHeight - 200 * scale) / 2}px) scale(${scale})`,
    );
  });

  it("keeps the real card hidden while the staged card travels", () => {
    // given
    const ref = hiddenTarget();
    const { run } = renderStage({ target: ref });

    // when
    run(1000 + 500);

    // then
    expect(ref.current?.style.visibility).toBe("hidden");
  });

  it("shows the real card under the staged one for the closing cross-fade", () => {
    // given
    const ref = hiddenTarget();
    const { run } = renderStage({ target: ref });

    // when
    run(1000 + 736);

    // then
    expect(ref.current?.style.visibility).toBe("visible");
    expect(Number(card().style.opacity)).toBeLessThan(1);
  });

  it("hands the real card back as it found it", () => {
    // given
    const ref = hiddenTarget();
    const { run } = renderStage({ target: ref });
    run(1000 + 736);

    // when
    cleanup();

    // then
    expect(ref.current?.style.visibility).toBe("hidden");
  });

  it("fades out in place, without showing a target that is another card", () => {
    // given
    const ref = hiddenTarget();
    const { run } = renderStage({ target: ref, settle: "fade" });

    // when
    run(1000 + 736);

    // then
    expect(ref.current?.style.visibility).toBe("hidden");
  });
});

describe("CeremonyStage under reduced motion", () => {
  it("fades the target in without a stage", () => {
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
    const ref = target();
    const animate = vi.fn();
    Object.assign(ref.current as HTMLElement, { animate });

    // when
    renderStage({ target: ref });

    // then
    expect(animate).toHaveBeenCalledWith([{ opacity: 0 }, { opacity: 1 }], {
      duration: 200,
      easing: "ease-out",
    });
  });
});
