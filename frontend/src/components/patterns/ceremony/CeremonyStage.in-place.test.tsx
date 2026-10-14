import { act } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { moveTarget, renderStage, stage } from "./ceremony-stage.test-helpers";

afterEach(() => {
  moveTarget(new DOMRect(100, 80, 768, 400));
  vi.restoreAllMocks();
});

describe("CeremonyStage in place", () => {
  it("raises no backdrop", () => {
    // when
    renderStage({ placement: "in-place" });

    // then
    expect(document.querySelector("[data-ceremony-backdrop]")).toBeNull();
  });

  it("clips the stage to the target card", () => {
    // given
    vi.spyOn(document.documentElement, "clientWidth", "get").mockReturnValue(
      1280,
    );
    vi.spyOn(document.documentElement, "clientHeight", "get").mockReturnValue(
      720,
    );

    // when
    renderStage({ placement: "in-place" });

    // then
    expect(stage()?.style.clipPath).toBe(
      "inset(80px 412px 240px 100px round 0px)",
    );
  });

  it("starts the staged card on the target", () => {
    // when
    renderStage({ placement: "in-place" });

    // then
    const card = document.querySelector<HTMLElement>("[data-ceremony-card]");
    expect(card?.style.transform).toBe("translate(100px, 80px) scale(1)");
  });

  it("finishes when the page scrolls", () => {
    // given
    const { onDone } = renderStage({ placement: "in-place" });

    // when
    act(() => {
      window.dispatchEvent(new Event("scroll"));
    });

    // then
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("keeps the full-screen stage by default", () => {
    // when
    renderStage();

    // then
    expect(document.querySelector("[data-ceremony-backdrop]")).not.toBeNull();
    expect(stage()?.style.clipPath).toBe("");
  });
});
