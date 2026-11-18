import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  advance,
  billboard,
  isInert,
  placeTrack,
  slide,
  stubBillboardStage,
  track,
  WIDTH,
} from "./billboard.test-helpers";

stubBillboardStage();

describe("Billboard gestures", () => {
  it("holds still while a mouse hovers it", () => {
    // given
    render(billboard());
    const carousel = screen.getByRole("region", { name: "Featured" });

    // when
    fireEvent.pointerEnter(carousel, { pointerType: "mouse" });
    advance(30_000);
    const whileHovered = isInert(slide(1));
    fireEvent.pointerLeave(carousel);
    advance(7000);

    // then
    expect(whileHovered).toBe(false);
    expect(isInert(slide(2))).toBe(false);
  });

  it("holds still while keyboard focus is inside", () => {
    // given
    render(billboard());

    // when
    act(() => {
      screen.getByRole("button", { name: "Join Alpha" }).focus();
    });
    advance(30_000);

    // then
    expect(isInert(slide(1))).toBe(false);
  });

  it("keeps rotating after a mouse click leaves focus inside", () => {
    // given
    render(billboard());
    const pause = screen.getByRole("button", { name: "Pause" });

    // when
    fireEvent.pointerDown(pause);
    act(() => {
      pause.focus();
    });
    fireEvent.pointerUp(pause);
    act(() => {
      pause.blur();
    });
    advance(7000);

    // then
    expect(isInert(slide(2))).toBe(false);
  });

  it("lets the scroll position pick the active slide", () => {
    // given
    render(billboard());
    placeTrack(2 * WIDTH);

    // when
    fireEvent.scroll(track());

    // then
    expect(isInert(slide(3))).toBe(false);
  });

  it("ignores the slides a programmatic scroll passes through", () => {
    // given
    render(billboard());
    fireEvent.click(screen.getByRole("button", { name: "Slide 3" }));
    placeTrack(WIDTH);

    // when
    fireEvent.scroll(track());

    // then
    expect(isInert(slide(3))).toBe(false);
  });

  it("lets a swipe interrupt a programmatic scroll", () => {
    // given
    render(billboard());
    fireEvent.click(screen.getByRole("button", { name: "Slide 3" }));
    fireEvent.touchStart(track());
    placeTrack(WIDTH);

    // when
    fireEvent.scroll(track());

    // then
    expect(isInert(slide(2))).toBe(false);
  });

  it("treats a sideways wheel as a manual move", () => {
    // given
    render(billboard());

    // when
    fireEvent.wheel(track(), { deltaX: 40, deltaY: 2 });
    advance(12_000);
    advance(6999);

    // then
    expect(isInert(slide(1))).toBe(false);
  });

  it("moves one slide when a mouse drags past the threshold", () => {
    // given
    render(billboard());

    // when
    fireEvent.pointerDown(track(), {
      pointerType: "mouse",
      button: 0,
      clientX: 500,
    });
    fireEvent.pointerMove(track(), { pointerType: "mouse", clientX: 420 });
    fireEvent.pointerUp(track(), { pointerType: "mouse", clientX: 420 });

    // then
    expect(isInert(slide(2))).toBe(false);
  });

  it("springs back when a drag stays under the threshold", () => {
    // given
    render(billboard());
    fireEvent.click(screen.getByRole("button", { name: "Slide 2" }));
    placeTrack(WIDTH);

    // when
    fireEvent.pointerDown(track(), {
      pointerType: "mouse",
      button: 0,
      clientX: 500,
    });
    fireEvent.pointerUp(track(), { pointerType: "mouse", clientX: 530 });

    // then
    expect(isInert(slide(2))).toBe(false);
  });

  it("drags back a slide to the right", () => {
    // given
    render(billboard());
    fireEvent.click(screen.getByRole("button", { name: "Slide 2" }));
    placeTrack(WIDTH);

    // when
    fireEvent.pointerDown(track(), {
      pointerType: "mouse",
      button: 0,
      clientX: 300,
    });
    fireEvent.pointerMove(track(), { pointerType: "mouse", clientX: 400 });
    fireEvent.pointerCancel(track(), { pointerType: "mouse", clientX: 400 });

    // then
    expect(isInert(slide(1))).toBe(false);
  });

  it("swallows the click that ends a drag", () => {
    // given
    render(billboard());
    fireEvent.pointerDown(track(), {
      pointerType: "mouse",
      button: 0,
      clientX: 500,
    });
    fireEvent.pointerMove(track(), { pointerType: "mouse", clientX: 450 });
    fireEvent.pointerUp(track(), { pointerType: "mouse", clientX: 450 });

    // when
    const clicks = [fireEvent.click(track()), fireEvent.click(track())];

    // then
    expect(clicks).toEqual([false, true]);
  });

  it("does not drag from a touch or from a button", () => {
    // given
    render(billboard());

    // when
    fireEvent.pointerDown(track(), { pointerType: "touch", clientX: 500 });
    fireEvent.pointerDown(screen.getByRole("button", { name: "Join Alpha" }), {
      pointerType: "mouse",
      button: 0,
      clientX: 500,
    });
    fireEvent.pointerUp(track(), { pointerType: "mouse", clientX: 100 });

    // then
    expect(isInert(slide(1))).toBe(false);
    expect(Element.prototype.setPointerCapture).not.toHaveBeenCalled();
  });

  it("does not count a plain click on a slide as a manual move", () => {
    // given
    render(billboard());

    // when
    fireEvent.pointerDown(track(), {
      pointerType: "mouse",
      button: 0,
      clientX: 500,
    });
    fireEvent.pointerUp(track(), { pointerType: "mouse", clientX: 501 });
    advance(7000);

    // then
    expect(isInert(slide(2))).toBe(false);
  });

  it("holds for keyboard focus after a touch swipe ends in a cancel", () => {
    // given
    render(billboard());
    const join = screen.getByRole("button", { name: "Join Alpha" });
    fireEvent.pointerDown(join, { pointerType: "touch" });
    fireEvent.pointerCancel(join, { pointerType: "touch" });

    // when
    act(() => {
      join.focus();
    });
    advance(30_000);

    // then
    expect(isInert(slide(1))).toBe(false);
  });
});
