import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { snapshotFixture } from "@/features/health/domain/snapshot-fixture";
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
        ],
      },
    });

    // when
    render(<PayoffPanel snapshot={snapshot} />);

    // then
    expect(
      screen.getByText("0 saved, none confirmed helpful yet."),
    ).toBeVisible();
    expect(
      screen.queryByText("Sessions keep a steady rhythm."),
    ).not.toBeInTheDocument();
  });
});
