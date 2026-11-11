import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AverageBars } from "@/components/patterns/average-bars/AverageBars";

function fills(container: HTMLElement): string[] {
  return [...container.querySelectorAll("[data-bar]")].map(
    (bar) =>
      bar.querySelector<HTMLElement>("[data-filled]")?.style.clipPath ?? "off",
  );
}

describe("AverageBars", () => {
  it("marks 7.4 as seven full bars and one bar filled to 40%", () => {
    // when
    const { container } = render(<AverageBars value={7.4} label="Learned" />);

    // then
    expect(fills(container)).toEqual([
      ...Array.from({ length: 7 }, () => "inset(0 0% 0 0)"),
      "inset(0 60% 0 0)",
      "off",
      "off",
    ]);
  });

  it("announces the average out of 10", () => {
    // when
    render(<AverageBars value={7.4} label="Learned" />);

    // then
    expect(
      screen.getByRole("img", { name: "Learned 7.4 out of 10" }),
    ).toBeInTheDocument();
  });

  it("announces a whole average to one decimal and fills every bar at 10", () => {
    // when
    const { container } = render(<AverageBars value={10} label="Enjoyed" />);

    // then
    expect(
      screen.getByRole("img", { name: "Enjoyed 10.0 out of 10" }),
    ).toBeInTheDocument();
    expect(fills(container)).not.toContain("off");
  });

  it("rises from the first bar to the last", () => {
    // when
    const { container } = render(<AverageBars value={3} label="Learned" />);

    // then
    const heights = [
      ...container.querySelectorAll<HTMLElement>("[data-bar]"),
    ].map((bar) => Number.parseInt(bar.style.height, 10));
    const rising = [...heights];
    rising.sort((a, b) => a - b);
    expect(heights).toHaveLength(10);
    expect(heights).toEqual(rising);
    expect(new Set(heights).size).toBe(10);
  });

  it("leaves every bar off and says so when there is no average", () => {
    // when
    const { container } = render(<AverageBars value={null} label="Learned" />);

    // then
    expect(fills(container).every((fill) => fill === "off")).toBe(true);
    expect(
      screen.getByRole("img", { name: "Learned no answers" }),
    ).toBeInTheDocument();
  });
});
