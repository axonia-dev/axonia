import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { SessionTimeline } from "@/features/health/ui/SessionTimeline";

const GENERATED_AT = "2026-11-09T12:00:00Z";

function marks(container: HTMLElement, kind: string): HTMLElement[] {
  return [
    ...container.querySelectorAll<HTMLElement>(`[data-session='${kind}']`),
  ];
}

describe("SessionTimeline", () => {
  it("places each held session at its real date across the window", () => {
    // when
    const { container } = render(
      <SessionTimeline
        heldSessionDates={["2026-10-10T12:00:00Z", "2026-11-09T12:00:00Z"]}
        nextSessionAt={null}
        windowId="60d"
        generatedAt={GENERATED_AT}
      />,
    );

    // then
    expect(marks(container, "held").map((mark) => mark.style.left)).toEqual([
      "50%",
      "100%",
    ]);
  });

  it("drops a date outside the window rather than drawing it off the edge", () => {
    // when
    const { container } = render(
      <SessionTimeline
        heldSessionDates={["2026-01-01T12:00:00Z", "2026-11-01T12:00:00Z"]}
        nextSessionAt={null}
        windowId="90d"
        generatedAt={GENERATED_AT}
      />,
    );

    // then
    expect(marks(container, "held")).toHaveLength(1);
  });

  it("draws the next session as a dashed mark at the far end", () => {
    // when
    const { container } = render(
      <SessionTimeline
        heldSessionDates={[]}
        nextSessionAt="2026-12-04T17:00:00Z"
        windowId="90d"
        generatedAt={GENERATED_AT}
      />,
    );

    // then
    const [next] = marks(container, "next");
    expect(next).toHaveClass("border-dashed");
    expect(next?.style.left).toBe("100%");
  });

  it("names a window without sessions for a screen reader", () => {
    // when
    render(
      <SessionTimeline
        heldSessionDates={[]}
        nextSessionAt={null}
        windowId="30d"
        generatedAt={GENERATED_AT}
      />,
    );

    // then
    expect(
      screen.getByRole("img", { name: "0 sessions held in the last 30 days" }),
    ).toBeInTheDocument();
  });
});
