import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  advance,
  billboard,
  isInert,
  slide,
  slides,
  stubBillboardStage,
} from "./billboard.test-helpers";

stubBillboardStage();

describe("Billboard while suspended", () => {
  it("holds still and keeps the active slide reachable", () => {
    // given
    render(billboard(slides, { suspended: true }));
    const join = screen.getByRole("button", { name: "Join Alpha" });
    act(() => {
      join.focus();
    });

    // when
    act(() => {
      join.blur();
    });
    advance(30_000);

    // then
    expect(isInert(slide(1))).toBe(false);
  });

  it("resumes after the post-move grace once uncovered", () => {
    // given
    const { rerender } = render(billboard(slides, { suspended: true }));
    advance(30_000);

    // when
    rerender(billboard(slides, { suspended: false }));
    advance(12_000);
    advance(6999);
    const beforeInterval = isInert(slide(1));
    advance(1);

    // then
    expect(beforeInterval).toBe(false);
    expect(isInert(slide(2))).toBe(false);
  });
});

describe("Billboard announcements and end arrows", () => {
  it("announces the slide a dot or arrow moved to", () => {
    // given
    render(billboard(slides, { itemLabel: (item) => item.name }));
    const status = screen.getByRole("status");
    const quiet = status.textContent;

    // when
    fireEvent.click(screen.getByRole("button", { name: "Show Charlie" }));

    // then
    expect(quiet).toBe("");
    expect(status).toHaveTextContent("Charlie, 3 of 3");
  });

  it("keeps an end arrow focusable and does nothing when pressed", () => {
    // given
    render(billboard());
    const next = screen.getByRole("button", { name: "Next slide" });
    fireEvent.click(screen.getByRole("button", { name: "Slide 3" }));
    act(() => {
      next.focus();
    });

    // when
    fireEvent.click(next);

    // then
    expect(next).toHaveFocus();
    expect(next).toHaveAttribute("aria-disabled", "true");
    expect(isInert(slide(3))).toBe(false);
  });

  it("ignores the previous arrow on the first slide", () => {
    // given
    render(billboard());

    // when
    fireEvent.click(screen.getByRole("button", { name: "Previous slide" }));

    // then
    expect(screen.getByRole("status")).toHaveTextContent("");
    expect(isInert(slide(1))).toBe(false);
  });
});

describe("Billboard controls placement", () => {
  it("keeps the default inset without a consumer class", () => {
    // when
    render(billboard());

    // then
    const controls = screen.getByRole("button", { name: "Next slide" })
      .parentElement as HTMLElement;
    expect(controls).toHaveClass("md:right-6");
  });

  it("lets a consumer replace the controls' inset", () => {
    // when
    render(
      billboard(slides, {
        controlsClassName: "md:right-[max(2.5rem,calc(50%-36rem))]",
      }),
    );

    // then
    const controls = screen.getByRole("button", { name: "Next slide" })
      .parentElement as HTMLElement;
    expect(controls).toHaveClass("md:right-[max(2.5rem,calc(50%-36rem))]");
    expect(controls).not.toHaveClass("md:right-6");
  });
});
