import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { OpenRing } from "./OpenRing";

describe("OpenRing", () => {
  it("wrapsItsChildInADashedTurningRing", () => {
    // when
    const { container } = render(
      <OpenRing dot>
        <span>face</span>
      </OpenRing>,
    );

    // then
    const svg = container.querySelector("svg[aria-hidden='true']");
    expect(svg).toHaveClass("animate-open-ring");
    expect(svg?.querySelector("circle")).toHaveAttribute(
      "stroke-linecap",
      "round",
    );
    expect(screen.getByText("face").parentElement).toHaveClass("scale-80");
  });

  it("drawsTheSpeechDotOnlyWhenAsked", () => {
    // when
    render(
      <OpenRing dot={false}>
        <span>face</span>
      </OpenRing>,
    );

    // then
    expect(screen.queryByTestId("open-dot")).not.toBeInTheDocument();
  });

  it("saysOpenToQuestionsToAScreenReaderOnlyWhenAsked", () => {
    // when
    render(
      <>
        <OpenRing dot={false} labelled>
          <span>labelled face</span>
        </OpenRing>
        <OpenRing dot={false}>
          <span>plain face</span>
        </OpenRing>
      </>,
    );

    // then
    const label = screen.getByText("Open to questions");
    expect(label).toHaveClass("sr-only");
    expect(screen.getAllByText("Open to questions")).toHaveLength(1);
  });
});
