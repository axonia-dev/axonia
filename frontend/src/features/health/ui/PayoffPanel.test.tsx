import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  learningFixture,
  snapshotFixture,
} from "@/features/health/domain/snapshot-fixture";
import { PayoffPanel } from "@/features/health/ui/PayoffPanel";

describe("PayoffPanel", () => {
  it("is headed by its question and judged by the payoff pill", () => {
    // when
    render(<PayoffPanel snapshot={snapshotFixture()} />);

    // then
    expect(
      screen.getByRole("heading", { name: "Is it paying off" }),
    ).toBeVisible();
    expect(screen.getByText("No payoff yet")).toHaveAttribute(
      "data-level",
      "QUIET",
    );
  });

  it("is judged by the combined payoff, not by reuse alone", () => {
    // given
    const snapshot = snapshotFixture({
      learning: learningFixture,
      payoff: { code: "LEARNING_A_LOT", level: "GREAT" },
    });

    // when
    render(<PayoffPanel snapshot={snapshot} />);

    // then
    expect(screen.getByText("Learning a lot")).toHaveAttribute(
      "data-level",
      "GREAT",
    );
    expect(screen.queryByText("No payoff yet")).not.toBeInTheDocument();
  });

  it("sets the Library beside what was said in the room", () => {
    // when
    render(
      <PayoffPanel snapshot={snapshotFixture({ learning: learningFixture })} />,
    );

    // then
    expect(screen.getByRole("heading", { name: "Library" })).toBeVisible();
    expect(screen.getByRole("heading", { name: "In the room" })).toBeVisible();
    expect(
      screen.getByRole("img", { name: "Learned 8.3 out of 10" }),
    ).toBeVisible();
  });

  it("reads the funnel in three steps", () => {
    // when
    render(<PayoffPanel snapshot={snapshotFixture()} />);

    // then
    expect(screen.getByText("opened from the Library")).toBeVisible();
    expect(screen.getByText("saved to come back to")).toBeVisible();
    expect(screen.getByText("confirmed it helped")).toBeVisible();
  });

  it("states its findings beneath its evidence", () => {
    // given
    const snapshot = snapshotFixture({
      diagnosis: {
        findings: [
          { kind: "GAP", code: "INTEREST_WITHOUT_PAYOFF" },
          { kind: "STRENGTH", code: "ON_RHYTHM" },
          { kind: "STRENGTH", code: "LEARNING_LANDS" },
        ],
      },
      learning: learningFixture,
    });

    // when
    render(<PayoffPanel snapshot={snapshot} />);

    // then
    expect(
      screen.getByText("0 saved, none confirmed helpful yet."),
    ).toBeVisible();
    expect(
      screen.getByText("Attendees report learning a lot: 8.3 out of 10."),
    ).toBeVisible();
    expect(
      screen.queryByText("Sessions keep a steady rhythm."),
    ).not.toBeInTheDocument();
  });
});
