import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { CadencePulse, positionsOf } from "@/features/health/ui/CadencePulse";

const NOW = "2026-06-11T12:00:00Z";

describe("positionsOf", () => {
  it("places a session at the far right when it happened just now", () => {
    expect(positionsOf([NOW], 90, NOW)[0]).toBeCloseTo(1, 3);
  });

  it("places a session at the far left when it happened at the window's start", () => {
    expect(positionsOf(["2026-03-13T12:00:00Z"], 90, NOW)[0]).toBeCloseTo(0, 2);
  });

  it("places a mid-window session in the middle", () => {
    expect(positionsOf(["2026-04-27T12:00:00Z"], 90, NOW)[0]).toBeCloseTo(
      0.5,
      2,
    );
  });

  it("drops a date outside the window rather than drawing it off the edge", () => {
    expect(positionsOf(["2025-01-01T00:00:00Z"], 90, NOW)).toEqual([]);
  });
});

describe("CadencePulse", () => {
  it("draws one spike per session that really happened", () => {
    render(
      <CadencePulse
        heldSessionDates={["2026-04-27T12:00:00Z", "2026-05-20T12:00:00Z"]}
        windowDays={90}
        generatedAt={NOW}
      />,
    );

    expect(screen.getByRole("img").querySelectorAll("circle")).toHaveLength(2);
  });

  it("draws a dormant community as a flat line with no spikes", () => {
    render(
      <CadencePulse heldSessionDates={[]} windowDays={90} generatedAt={NOW} />,
    );

    expect(screen.getByRole("img").querySelectorAll("circle")).toHaveLength(0);
  });

  it("draws only a dashed baseline for a dormant community, not a solid axis", () => {
    render(
      <CadencePulse heldSessionDates={[]} windowDays={90} generatedAt={NOW} />,
    );

    const svg = screen.getByRole("img");
    const lines = svg.querySelectorAll("line");

    expect(lines).toHaveLength(1);
    expect(lines[0]?.getAttribute("stroke")).toBe("var(--border-strong)");
    expect(lines[0]?.getAttribute("stroke-dasharray")).toBe("4 6");
    expect(svg.querySelector("path")).toBeNull();
  });

  it("names what it is drawing for a screen reader", () => {
    render(
      <CadencePulse
        heldSessionDates={["2026-04-27T12:00:00Z"]}
        windowDays={90}
        generatedAt={NOW}
      />,
    );

    expect(screen.getByRole("img")).toHaveAccessibleName(
      "1 session held over the window",
    );
  });
});
