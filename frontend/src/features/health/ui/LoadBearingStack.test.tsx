import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  carriersOf,
  LoadBearingStack,
} from "@/features/health/ui/LoadBearingStack";

describe("carriersOf", () => {
  it("counts the segments needed to cross the halfway line, which is the bus factor", () => {
    expect(carriersOf([0.66, 0.17, 0.17])).toBe(1);
    expect(carriersOf([0.3, 0.3, 0.2, 0.2])).toBe(2);
    expect(
      carriersOf([0.16, 0.14, 0.12, 0.11, 0.1, 0.09, 0.08, 0.07, 0.13]),
    ).toBe(4);
  });

  it("counts a single contributor carrying everything as one", () => {
    expect(carriersOf([1])).toBe(1);
  });

  it("counts nobody when nobody contributed", () => {
    expect(carriersOf([])).toBe(0);
  });

  it("counts an exactly-half first segment as carrying the half", () => {
    // The backend's Distribution.busFactor uses >= 0.5 with a 1e-9 tolerance; matching it here is
    // what keeps the drawn crossing and the printed number from ever disagreeing.
    expect(carriersOf([0.5, 0.25, 0.25])).toBe(1);
  });
});

describe("LoadBearingStack", () => {
  it("draws one segment per contributor", () => {
    render(
      <LoadBearingStack distribution={[0.5, 0.3, 0.2]} tone="var(--warning)" />,
    );

    expect(
      screen.getByLabelText("Anonymous contribution distribution").children,
    ).toHaveLength(3);
  });

  it("says how many carry the first half and how many contributed at all", () => {
    render(
      <LoadBearingStack
        distribution={[0.66, 0.17, 0.17]}
        tone="var(--warning)"
      />,
    );

    expect(screen.getByText(/person carries the first half/)).toHaveTextContent(
      "1 person carries the first half",
    );
    expect(screen.getByText(/3 contributed in total/)).toBeInTheDocument();
  });

  it("carries no per-segment label, because position is the only identity a segment has", () => {
    render(
      <LoadBearingStack
        distribution={[0.66, 0.17, 0.17]}
        tone="var(--warning)"
      />,
    );

    const bar = screen.getByLabelText("Anonymous contribution distribution");

    expect(bar.textContent).toBe("");
    expect(bar.querySelector("[title]")).toBeNull();
  });

  it("states the emptiness plainly when nobody contributed", () => {
    render(<LoadBearingStack distribution={[]} tone="var(--danger)" />);

    expect(
      screen.getByText("Nobody contributed in this window."),
    ).toBeInTheDocument();
  });
});
