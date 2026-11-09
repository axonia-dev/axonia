import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { HealthSnapshot } from "@/features/health/domain/health";
import { ReuseFunnel } from "@/features/health/ui/ReuseFunnel";

function reuse(
  overrides: Partial<HealthSnapshot["reuse"]> = {},
): HealthSnapshot["reuse"] {
  return {
    opened: 214,
    artifactsOpened: 18,
    openingsTrend: { previous: 151, direction: "UP" },
    saves: 37,
    artifactsSaved: 12,
    helped: 9,
    notHelped: 4,
    savesTrend: { previous: 40, direction: "DOWN" },
    status: { code: "PAYING_OFF", level: "GREAT" },
    ...overrides,
  };
}

function fillWidths(container: HTMLElement): number[] {
  return [...container.querySelectorAll<HTMLElement>('[style*="width"]')].map(
    (fill) => Number.parseFloat(fill.style.width),
  );
}

describe("ReuseFunnel", () => {
  it("reads across, widest first", () => {
    // when
    render(<ReuseFunnel reuse={reuse()} />);

    // then
    const steps = screen.getAllByRole("listitem");
    expect(steps[0]).toHaveTextContent("214");
    expect(steps[1]).toHaveTextContent("37");
    expect(steps[2]).toHaveTextContent("9");
  });

  it("gives openings and saves their own trend", () => {
    // when
    render(<ReuseFunnel reuse={reuse()} />);

    // then
    expect(screen.getByText("up from 151")).toBeVisible();
    expect(screen.getByText("down from 40")).toBeVisible();
  });

  it("gives confirmed helpful no trend, because it is the claim and not a flow", () => {
    // when
    render(<ReuseFunnel reuse={reuse()} />);

    // then
    expect(screen.getAllByText(/^(up|down) from \d/)).toHaveLength(2);
  });

  it("never sums the three readings", () => {
    // when
    render(<ReuseFunnel reuse={reuse()} />);

    // then
    expect(screen.queryByText("260")).not.toBeInTheDocument();
  });

  it("sizes each bar against the widest reading", () => {
    // when
    const { container } = render(<ReuseFunnel reuse={reuse()} />);

    // then
    expect(fillWidths(container).map(Math.round)).toEqual([100, 17, 4]);
  });

  it("keeps every fill inside its track when a later reading outruns the first", () => {
    // Saves can outrun openings across a window boundary. Sizing against openings alone would run
    // the saved fill past the end of its own track.
    const { container } = render(
      <ReuseFunnel reuse={reuse({ opened: 5, saves: 40, helped: 2 })} />,
    );

    // then
    expect(fillWidths(container)).toEqual([12.5, 100, 5]);
  });

  it("draws empty tracks when nothing was opened", () => {
    // when
    const { container } = render(
      <ReuseFunnel reuse={reuse({ opened: 0, saves: 0, helped: 0 })} />,
    );

    // then
    expect(fillWidths(container)).toEqual([0, 0, 0]);
  });
});
