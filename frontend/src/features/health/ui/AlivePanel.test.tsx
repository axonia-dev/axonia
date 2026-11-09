import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { HealthSnapshot } from "@/features/health/domain/health";
import {
  snapshotFixture,
  troubledSnapshotFixture,
} from "@/features/health/domain/snapshot-fixture";
import { AlivePanel } from "@/features/health/ui/AlivePanel";
import { factText as fact } from "@/test/health-view";

function withCadence(
  cadence: Partial<HealthSnapshot["cadence"]>,
  snapshot = snapshotFixture(),
): HealthSnapshot {
  return { ...snapshot, cadence: { ...snapshot.cadence, ...cadence } };
}

describe("AlivePanel", () => {
  it("is headed by its question and judged by the alive pill", () => {
    // when
    render(<AlivePanel snapshot={snapshotFixture()} />);

    // then
    expect(screen.getByRole("heading", { name: "Is it alive" })).toBeVisible();
    expect(screen.getByText("On rhythm")).toHaveAttribute("data-level", "GOOD");
  });

  it("states the typical gap between sessions", () => {
    // when
    render(<AlivePanel snapshot={snapshotFixture()} />);

    // then
    expect(fact("typical gap between sessions")).toBe(
      "32 days typical gap between sessions",
    );
    expect(screen.queryByText(/median/i)).not.toBeInTheDocument();
  });

  it("states a gap under a day as such rather than as zero days", () => {
    // when
    render(<AlivePanel snapshot={withCadence({ medianGapDays: 0 })} />);

    // then
    expect(fact("typical gap between sessions")).toBe(
      "Under a day typical gap between sessions",
    );
  });

  it("states the next session's date", () => {
    // when
    render(<AlivePanel snapshot={snapshotFixture()} />);

    // then
    expect(fact("next session")).toBe("4 Dec next session");
  });

  it("dates the next session in the reader's zone, so a 19:00 New York session on 4 Dec reads 4 Dec", () => {
    // when
    render(
      <AlivePanel
        snapshot={withCadence({ nextSessionAt: "2026-12-05T00:00:00Z" })}
      />,
    );

    // then
    expect(fact("next session")).toBe("4 Dec next session");
  });

  it("states none scheduled when nothing is ahead", () => {
    // when
    render(<AlivePanel snapshot={withCadence({ nextSessionAt: null })} />);

    // then
    expect(fact("scheduled")).toBe("None scheduled");
  });

  it("states the days since the last session when there is no gap to state", () => {
    // when
    render(<AlivePanel snapshot={troubledSnapshotFixture()} />);

    // then
    expect(fact("since the last session")).toBe(
      "61 days since the last session",
    );
    expect(
      screen.queryByText("typical gap between sessions"),
    ).not.toBeInTheDocument();
  });

  it("states the days since the last session ever held when nothing was held in the window", () => {
    // given
    const snapshot = withCadence({
      rhythm: "STALLED",
      sessionsHeld: 0,
      medianGapDays: null,
      lastHeldAt: "2026-06-01T12:00:00Z",
      status: { code: "STALLED", level: "WORRY" },
    });
    snapshot.heldSessionDates = [];

    // when
    render(<AlivePanel snapshot={snapshot} />);

    // then
    expect(fact("since the last session")).toBe(
      "161 days since the last session",
    );
  });

  it("states no gap at all for a community that never held a session", () => {
    // given
    const snapshot = withCadence({
      rhythm: "DORMANT",
      sessionsHeld: 0,
      medianGapDays: null,
      nextSessionAt: null,
      lastHeldAt: null,
      status: { code: "DORMANT", level: "WORRY" },
    });
    snapshot.heldSessionDates = [];

    // when
    render(<AlivePanel snapshot={snapshot} />);

    // then
    expect(
      screen.queryByText("since the last session"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText("typical gap between sessions"),
    ).not.toBeInTheDocument();
    expect(fact("scheduled")).toBe("None scheduled");
  });

  it("draws each held session and the next one on a timeline", () => {
    // when
    render(<AlivePanel snapshot={snapshotFixture()} />);

    // then
    expect(
      screen.getByRole("img", {
        name: "3 sessions held in the last 90 days, the next on 4 Dec",
      }),
    ).toBeVisible();
  });

  it("counts joins and loves with their trends", () => {
    // when
    render(<AlivePanel snapshot={snapshotFixture()} />);

    // then
    expect(fact("joined")).toBe("2 up from 0 joined");
    expect(fact("love given")).toBe("1 up from 0 love given");
  });

  it("pluralises loves given", () => {
    // given
    const snapshot = snapshotFixture({
      engagement: { loves: 4, trend: { previous: 4, direction: "FLAT" } },
    });

    // when
    render(<AlivePanel snapshot={snapshot} />);

    // then
    expect(fact("loves given")).toBe("4 same as before loves given");
  });

  it("states its findings beneath its evidence", () => {
    // given
    const snapshot = snapshotFixture({
      diagnosis: {
        findings: [
          { kind: "STRENGTH", code: "ON_RHYTHM" },
          { kind: "RISK", code: "SINGLE_POINT" },
        ],
      },
    });

    // when
    render(<AlivePanel snapshot={snapshot} />);

    // then
    expect(screen.getByText("Sessions keep a steady rhythm.")).toBeVisible();
    expect(
      screen.queryByText("Every talk came from one person."),
    ).not.toBeInTheDocument();
  });
});
