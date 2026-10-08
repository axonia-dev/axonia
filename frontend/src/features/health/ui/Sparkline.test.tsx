import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Sparkline } from "@/features/health/ui/Sparkline";

describe("Sparkline", () => {
  it("labels itself for a screen reader, since the shape alone says nothing", () => {
    render(<Sparkline values={[1, 2, 3]} label="contributors per week" />);

    expect(screen.getByRole("img")).toHaveAccessibleName(
      "contributors per week",
    );
  });

  it("draws one point per value", () => {
    render(<Sparkline values={[0, 5, 2, 8]} label="loves" />);

    const points = screen
      .getByRole("img")
      .querySelector("polyline")
      ?.getAttribute("points");

    expect(points?.trim().split(/\s+/)).toHaveLength(4);
  });

  it("draws a flat series as a flat line rather than dividing by zero", () => {
    render(<Sparkline values={[3, 3, 3, 3]} label="joins" />);

    const points = screen
      .getByRole("img")
      .querySelector("polyline")
      ?.getAttribute("points");

    expect(points).not.toContain("NaN");
  });

  it("draws an all-zero series without dividing by zero", () => {
    render(<Sparkline values={[0, 0, 0]} label="saves" />);

    expect(
      screen.getByRole("img").querySelector("polyline")?.getAttribute("points"),
    ).not.toContain("NaN");
  });

  it("renders nothing for an empty series rather than an empty box", () => {
    const { container } = render(<Sparkline values={[]} label="saves" />);

    expect(container).toBeEmptyDOMElement();
  });

  it("fills the area under the line with a gradient fading from the tone to transparent", () => {
    render(<Sparkline values={[0, 5, 2, 8]} label="loves" tone="red" />);

    const svg = screen.getByRole("img");
    const area = svg.querySelector("polygon");
    const gradient = svg.querySelector("linearGradient");

    expect(area).not.toBeNull();
    expect(gradient).not.toBeNull();

    const stops = gradient!.querySelectorAll("stop");

    expect(area!.getAttribute("points")).not.toContain("NaN");
    expect(area!.getAttribute("fill")).toBe(`url(#${gradient!.id})`);
    expect(stops).toHaveLength(2);
    expect(stops[0]!.getAttribute("stop-color")).toBe("red");
    expect(stops[0]!.getAttribute("stop-opacity")).toBe(".28");
    expect(stops[1]!.getAttribute("stop-opacity")).toBe("0");
  });

  it("draws the area without dividing by zero for a flat series", () => {
    render(<Sparkline values={[3, 3, 3, 3]} label="joins" />);

    const area = screen.getByRole("img").querySelector("polygon");

    expect(area?.getAttribute("points")).not.toContain("NaN");
  });

  it("draws the area without dividing by zero for an all-zero series", () => {
    render(<Sparkline values={[0, 0, 0]} label="saves" />);

    const area = screen.getByRole("img").querySelector("polygon");

    expect(area?.getAttribute("points")).not.toContain("NaN");
  });

  it("gives each sparkline its own gradient id so several on one page never collide", () => {
    render(
      <>
        <Sparkline values={[1, 2]} label="first" />
        <Sparkline values={[3, 4]} label="second" />
      </>,
    );

    const gradients = screen
      .getAllByRole("img")
      .map((svg) => svg.querySelector("linearGradient")?.id);

    expect(gradients[0]).not.toBe(gradients[1]);
    expect(gradients[0]).toBeTruthy();
    expect(gradients[1]).toBeTruthy();
  });

  it("marks the last point with a dot in the tone", () => {
    render(<Sparkline values={[0, 5, 2, 8]} label="loves" tone="red" />);

    const dot = screen.getByRole("img").querySelector("circle");

    expect(dot).not.toBeNull();
    expect(dot?.getAttribute("fill")).toBe("red");
    expect(dot?.getAttribute("r")).toBe("2.2");
  });

  it("still draws a dot for a single value", () => {
    render(<Sparkline values={[4]} label="loves" />);

    expect(screen.getByRole("img").querySelector("circle")).not.toBeNull();
    expect(
      screen.getByRole("img").querySelector("polygon")?.getAttribute("points"),
    ).not.toContain("NaN");
  });
});
