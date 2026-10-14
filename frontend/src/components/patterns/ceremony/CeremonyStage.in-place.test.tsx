import { act, fireEvent } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import {
  moveTarget,
  renderStage,
  stage,
  target,
} from "./ceremony-stage.test-helpers";

function keyOnPage(key: string): KeyboardEvent {
  const event = new KeyboardEvent("keydown", {
    key,
    cancelable: true,
    bubbles: true,
  });
  act(() => {
    document.body.dispatchEvent(event);
  });
  return event;
}

function scrollerHolding(card: HTMLElement | null): HTMLElement {
  const scroller = document.createElement("main");
  if (card !== null) {
    scroller.append(card);
  }
  document.body.append(scroller);
  return scroller;
}

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

  it("finishes when the window resizes, which a fixed clip no longer matches", () => {
    // given
    const { onDone } = renderStage({ placement: "in-place" });

    // when
    act(() => {
      window.dispatchEvent(new Event("resize"));
    });

    // then
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("finishes on a key and lets the key reach the page", () => {
    // given
    const { onDone } = renderStage({ placement: "in-place" });
    const reachedPage = vi.fn();
    document.addEventListener("keydown", reachedPage);

    // when
    const event = keyOnPage("a");

    // then
    document.removeEventListener("keydown", reachedPage);
    expect(onDone).toHaveBeenCalledOnce();
    expect(reachedPage).toHaveBeenCalledOnce();
    expect(event.defaultPrevented).toBe(false);
  });

  it("finishes when the scroller holding the card scrolls", () => {
    // given
    const card = target();
    const scroller = scrollerHolding(card.current);
    const { onDone } = renderStage({ placement: "in-place", target: card });

    // when
    fireEvent.scroll(scroller);

    // then
    expect(onDone).toHaveBeenCalledOnce();
    scroller.remove();
  });

  it("plays on when a scroller beside the card scrolls", () => {
    // given
    const card = target();
    const holder = scrollerHolding(card.current);
    const rail = scrollerHolding(null);
    const { onDone } = renderStage({ placement: "in-place", target: card });

    // when
    fireEvent.scroll(rail);

    // then
    expect(onDone).not.toHaveBeenCalled();
    holder.remove();
    rail.remove();
  });

  it("keeps the full-screen stage by default", () => {
    // when
    renderStage();

    // then
    expect(document.querySelector("[data-ceremony-backdrop]")).not.toBeNull();
    expect(stage()?.style.clipPath).toBe("");
  });

  it("plays the full-screen stage on through a resize", () => {
    // given
    const { onDone } = renderStage();

    // when
    act(() => {
      window.dispatchEvent(new Event("resize"));
    });

    // then
    expect(onDone).not.toHaveBeenCalled();
  });

  it("plays the full-screen stage on through a scroll", () => {
    // given
    const { onDone } = renderStage();

    // when
    act(() => {
      window.dispatchEvent(new Event("scroll"));
    });

    // then
    expect(onDone).not.toHaveBeenCalled();
  });
});
