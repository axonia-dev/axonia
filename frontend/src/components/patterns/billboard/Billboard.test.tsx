import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import {
  advance,
  billboard,
  isInert,
  slide,
  slides,
  stubBillboardStage,
  stubReducedMotion,
  track,
  WIDTH,
} from "./billboard.test-helpers";

const stage = stubBillboardStage();

describe("Billboard", () => {
  it("renders a labelled carousel with every slide in position", () => {
    // when
    render(billboard());

    // then
    const carousel = screen.getByRole("region", { name: "Featured" });
    expect(carousel).toHaveAttribute("aria-roledescription", "carousel");
    expect(
      [1, 2, 3].map((position) =>
        slide(position).getAttribute("aria-roledescription"),
      ),
    ).toEqual(["slide", "slide", "slide"]);
    expect(screen.getAllByRole("heading").map((h) => h.textContent)).toEqual([
      "Alpha",
      "Bravo",
      "Charlie",
    ]);
  });

  it("makes every slide but the active one inert", () => {
    // when
    render(billboard());

    // then
    expect([1, 2, 3].map((position) => isInert(slide(position)))).toEqual([
      false,
      true,
      true,
    ]);
    expect(screen.getByText("Alpha").parentElement).toHaveAttribute(
      "data-active",
      "true",
    );
  });

  it("opens on the slide it is told to, placed there without a scroll", () => {
    // when
    render(billboard(slides, { initialIndex: 2 }));

    // then
    expect([1, 2, 3].map((position) => isInert(slide(position)))).toEqual([
      true,
      true,
      false,
    ]);
    expect(track().scrollLeft).toBe(2 * WIDTH);
    expect(stage.scrollTo).not.toHaveBeenCalled();
  });

  it("moves to the next slide with the arrow", () => {
    // given
    render(billboard());

    // when
    fireEvent.click(screen.getByRole("button", { name: "Next slide" }));

    // then
    expect(stage.scrollTo).toHaveBeenCalledWith({
      left: WIDTH,
      behavior: "smooth",
    });
    expect(isInert(slide(2))).toBe(false);
    expect(screen.getByRole("button", { name: "Slide 2" })).toHaveAttribute(
      "aria-current",
      "true",
    );
  });

  it("moves back with the previous arrow, disabled on the first slide", () => {
    // given
    render(billboard());
    const previous = screen.getByRole("button", { name: "Previous slide" });
    const disabledAtStart = previous.getAttribute("aria-disabled");
    fireEvent.click(screen.getByRole("button", { name: "Slide 3" }));

    // when
    fireEvent.click(previous);

    // then
    expect(disabledAtStart).toBe("true");
    expect(stage.scrollTo).toHaveBeenLastCalledWith({
      left: WIDTH,
      behavior: "smooth",
    });
  });

  it("jumps to a slide with its dot and disables next on the last one", () => {
    // given
    render(billboard());

    // when
    fireEvent.click(screen.getByRole("button", { name: "Slide 3" }));

    // then
    expect(stage.scrollTo).toHaveBeenCalledWith({
      left: 2 * WIDTH,
      behavior: "smooth",
    });
    expect(screen.getByRole("button", { name: "Next slide" })).toHaveAttribute(
      "aria-disabled",
      "true",
    );
  });

  it("follows the arrow keys while focus is inside", () => {
    // given
    render(billboard());
    const join = screen.getByRole("button", { name: "Join Alpha" });
    join.focus();

    // when
    fireEvent.keyDown(join, { key: "ArrowRight" });

    // then
    expect(stage.scrollTo).toHaveBeenCalledWith({
      left: WIDTH,
      behavior: "smooth",
    });
    expect(document.activeElement).toBe(slide(2));
  });

  it("moves back with ArrowLeft", () => {
    // given
    render(billboard());
    fireEvent.click(screen.getByRole("button", { name: "Slide 2" }));

    // when
    fireEvent.keyDown(screen.getByRole("button", { name: "Slide 2" }), {
      key: "ArrowLeft",
    });

    // then
    expect(isInert(slide(1))).toBe(false);
  });

  it("ignores other keys and modified arrows", () => {
    // given
    render(billboard());
    const carousel = screen.getByRole("region", { name: "Featured" });

    // when
    fireEvent.keyDown(carousel, { key: "Enter" });
    fireEvent.keyDown(carousel, { key: "ArrowRight", metaKey: true });

    // then
    expect(isInert(slide(1))).toBe(false);
  });

  it("toggles the pause button between Pause and Play", () => {
    // given
    render(billboard());

    // when
    fireEvent.click(screen.getByRole("button", { name: "Pause" }));

    // then
    expect(screen.getByRole("button", { name: "Play" })).toBeInTheDocument();
    advance(70_000);
    expect(isInert(slide(1))).toBe(false);
  });

  it("advances on its own every 7 seconds", () => {
    // given
    render(billboard());

    // when
    advance(7000);

    // then
    expect(stage.scrollTo).toHaveBeenCalledWith({
      left: WIDTH,
      behavior: "smooth",
    });
    expect(isInert(slide(2))).toBe(false);
  });

  it("holds still for 12 seconds after a manual move", () => {
    // given
    render(billboard());
    fireEvent.click(screen.getByRole("button", { name: "Slide 2" }));

    // when
    advance(12_000);
    advance(6999);

    // then
    expect(isInert(slide(2))).toBe(false);
    advance(1);
    expect(isInert(slide(3))).toBe(false);
  });

  it("animates the active dot's progress over the interval", () => {
    // given
    const animate = vi.fn(() => ({ cancel: vi.fn() }));
    Element.prototype.animate =
      animate as unknown as typeof Element.prototype.animate;

    // when
    render(billboard());
    Reflect.deleteProperty(Element.prototype, "animate");

    // then
    expect(animate).toHaveBeenCalledWith(
      [{ transform: "scaleX(0)" }, { transform: "scaleX(1)" }],
      { duration: 7000, easing: "linear", fill: "forwards" },
    );
  });

  it("jumps without smoothing and offers no pause under reduced motion", () => {
    // given
    stubReducedMotion(true);
    render(billboard());

    // when
    fireEvent.click(screen.getByRole("button", { name: "Next slide" }));

    // then
    expect(stage.scrollTo).toHaveBeenCalledWith({
      left: WIDTH,
      behavior: "auto",
    });
    expect(
      screen.queryByRole("button", { name: "Pause" }),
    ).not.toBeInTheDocument();
  });

  it("renders one slide with no controls and never advances", () => {
    // given
    render(billboard(slides.slice(0, 1)));

    // when
    advance(70_000);

    // then
    expect(screen.getByRole("group", { name: "1 of 1" })).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /slide|pause|play/i }),
    ).not.toBeInTheDocument();
    expect(stage.scrollTo).not.toHaveBeenCalled();
  });

  it("renders nothing for an empty list", () => {
    // when
    const { container } = render(billboard([]));

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("names slides and dots after their item when given a label", () => {
    // when
    render(billboard(slides, { itemLabel: (item) => item.name }));

    // then
    expect(
      screen.getByRole("group", { name: "Bravo, 2 of 3" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Show Bravo" }),
    ).toBeInTheDocument();
  });

  it("paints overlay controls by default and theme controls on a surface", () => {
    // given
    const { unmount } = render(billboard());
    const overlay = screen.getByRole("button", {
      name: "Next slide",
    }).className;
    unmount();

    // when
    render(billboard(slides, { controlsTone: "surface" }));

    // then
    const surface = screen.getByRole("button", {
      name: "Next slide",
    }).className;
    expect(overlay).toContain("bg-black/45");
    expect(surface).toContain("bg-surface/85");
    expect(surface).not.toContain("bg-black/45");
  });
});
