import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ReuseFunnel } from "@/features/health/ui/ReuseFunnel";

const OPENINGS = [4, 2, 9, 0, 1, 3, 7, 2, 0, 5, 1, 4];
const SAVES = [1, 0, 2, 0, 0, 1, 0, 0, 3, 0, 0, 1];

function reuse(overrides = {}) {
  return {
    opened: 214,
    artifactsOpened: 18,
    openingsTrend: { previous: 151, direction: "UP" as const },
    saves: 37,
    artifactsSaved: 12,
    helped: 9,
    notHelped: 4,
    savesTrend: { previous: 40, direction: "DOWN" as const },
    ...overrides,
  };
}

function renderFunnel(overrides = {}) {
  return render(
    <ReuseFunnel reuse={reuse(overrides)} openings={OPENINGS} saves={SAVES} />,
  );
}

function fillWidths(container: HTMLElement): number[] {
  return [
    ...container.querySelectorAll<HTMLElement>('div[style*="width"]'),
  ].map((fill) => Number.parseFloat(fill.style.width));
}

describe("ReuseFunnel", () => {
  it("reads downward, widest first", () => {
    renderFunnel();

    const rows = screen.getAllByRole("listitem");

    expect(rows[0]).toHaveTextContent("214");
    expect(rows[1]).toHaveTextContent("37");
    expect(rows[2]).toHaveTextContent("9");
  });

  it("states the drop between steps rather than leaving it to be worked out", () => {
    renderFunnel();

    expect(screen.getByText("17% of the step above")).toBeInTheDocument();
    expect(screen.getByText("24% of the step above")).toBeInTheDocument();
  });

  it("gives each half of the funnel its own trend", () => {
    renderFunnel();

    expect(screen.getByText(/from 151/)).toBeInTheDocument();
    expect(screen.getByText(/from 40/)).toBeInTheDocument();
  });

  it("gives confirmed-helpful no trend, because it is the claim and not a flow", () => {
    renderFunnel();

    expect(screen.getAllByText(/from /)).toHaveLength(2);
  });

  it("draws the shape of each flow, and none for the claim they narrow to", () => {
    renderFunnel();

    expect(
      screen.getByRole("img", { name: "openings per bucket" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "saves per bucket" }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("img")).toHaveLength(2);
  });

  it("never sums the three readings", () => {
    renderFunnel();

    expect(screen.queryByText("260")).not.toBeInTheDocument();
  });

  it("draws no drop line above the first row", () => {
    renderFunnel({ opened: 0, saves: 0, helped: 0 });

    expect(screen.queryByText(/of the step above/)).not.toBeInTheDocument();
  });

  it("keeps every fill inside its track when a later reading outruns the first", () => {
    // Saves can outrun openings across a window boundary. Sizing against openings alone would run
    // the saved fill past the end of its own track.
    const { container } = renderFunnel({ opened: 5, saves: 40, helped: 2 });

    expect(fillWidths(container)).toEqual([12.5, 100, 5]);
  });
});
