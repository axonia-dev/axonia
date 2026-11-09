import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { StageBar } from "@/features/health/ui/StageBar";

const SHARES = [0.3, 0.25, 0.2, 0.15, 0.1];

function segments(container: HTMLElement): HTMLElement[] {
  return [...container.querySelectorAll<HTMLElement>("[data-stage-bar] > *")];
}

describe("StageBar", () => {
  it("draws one segment per share, widest first", () => {
    // when
    const { container } = render(
      <StageBar name="stage" shares={SHARES} strong={2} tone="data-1" />,
    );

    // then
    expect(
      segments(container).map((segment) => segment.style.flexGrow),
    ).toEqual(["0.3", "0.25", "0.2", "0.15", "0.1"]);
  });

  it("draws the half-covering segments in the strong tone and the rest off", () => {
    // when
    const { container } = render(
      <StageBar name="stage" shares={SHARES} strong={2} tone="data-1" />,
    );

    // then
    expect(segments(container).map((segment) => segment.className)).toEqual([
      expect.stringContaining("bg-data-1"),
      expect.stringContaining("bg-data-1"),
      expect.stringContaining("bg-border-strong"),
      expect.stringContaining("bg-border-strong"),
      expect.stringContaining("bg-border-strong"),
    ]);
  });

  it("gives a segment no name, no title and no focus, because position is its only identity", () => {
    // when
    const { container } = render(
      <StageBar name="stage" shares={SHARES} strong={2} tone="data-1" />,
    );

    // then
    for (const segment of segments(container)) {
      expect(segment).not.toHaveAttribute("aria-label");
      expect(segment).not.toHaveAttribute("title");
      expect(segment).not.toHaveAttribute("tabindex");
    }
    expect(container.querySelector("[data-stage-bar]")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
  });

  it("draws a thin bar in the second tone", () => {
    // when
    const { container } = render(
      <StageBar
        name="curation"
        shares={[0.9, 0.1]}
        strong={1}
        tone="data-2"
        thin
      />,
    );

    // then
    expect(segments(container)[0]).toHaveClass("bg-data-2");
    expect(container.querySelector("[data-stage-bar]")).toHaveClass("h-2");
  });
});
