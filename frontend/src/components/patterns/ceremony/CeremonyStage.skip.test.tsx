import { act, fireEvent } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { renderStage, stage } from "./stage-harness";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("CeremonyStage skipping", () => {
  it("finishes at once on any key", () => {
    // given
    const { onDone } = renderStage();

    // when
    fireEvent.keyDown(window, { key: "Escape" });

    // then
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("finishes at once on a click anywhere", () => {
    // given
    const { onDone } = renderStage();

    // when
    fireEvent.click(stage() as HTMLElement);

    // then
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("leaves a press alone so the tap that ends it never reaches the page", () => {
    // given
    const { onDone } = renderStage();

    // when
    fireEvent.pointerDown(stage() as HTMLElement);

    // then
    expect(onDone).not.toHaveBeenCalled();
  });

  it("keeps the skipping key from reaching the page", () => {
    // given
    renderStage();
    const reachedPage = vi.fn();
    document.addEventListener("keydown", reachedPage);

    // when
    const event = new KeyboardEvent("keydown", {
      key: "Enter",
      cancelable: true,
      bubbles: true,
    });
    act(() => {
      document.body.dispatchEvent(event);
    });

    // then
    document.removeEventListener("keydown", reachedPage);
    expect(reachedPage).not.toHaveBeenCalled();
    expect(event.defaultPrevented).toBe(true);
  });

  it("finishes when frames keep running slow", () => {
    // given
    const { run, onDone } = renderStage();

    // when
    run(600, 60);

    // then
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("forgives slow frames while the page is still mounting", () => {
    // given
    const { run, onDone } = renderStage();

    // when
    run(240, 60);

    // then
    expect(onDone).not.toHaveBeenCalled();
  });

  it("counts only consecutive slow frames", () => {
    // given
    const { run, onDone } = renderStage();
    run(300);

    // when
    run(120, 60);
    run(16);
    run(120, 60);

    // then
    expect(onDone).not.toHaveBeenCalled();
  });
});
