import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { snapshotFixture } from "@/features/health/domain/snapshot-fixture";
import {
  EVERY_FINDING,
  expectEveryFindingStatedOnce,
  mockLoaded,
  renderChapter,
  renderHealthView,
} from "@/test/health-view";

vi.mock("@/features/health/api/use-health", () => ({
  useHealthSnapshot: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());

describe("HealthView findings", () => {
  it("states each finding exactly once on the whole page", () => {
    // The bug this redesign exists to fix: the hero's nudge, the cadence read and the diagnosis
    // panel all said the same sentence. A `contains` assertion cannot catch an over-render, so this
    // counts.
    mockLoaded(
      snapshotFixture({
        busFactor: 1,
        diagnosis: {
          findings: [
            { kind: "RISK", code: "SINGLE_POINT" },
            { kind: "STRENGTH", code: "ON_RHYTHM" },
          ],
        },
      }),
    );

    renderHealthView();

    expect(
      screen.getAllByText("Everything leans on one person; spread the load."),
    ).toHaveLength(1);
    expect(
      screen.getAllByText("Sessions are happening on rhythm."),
    ).toHaveLength(1);
  });

  it("states every finding exactly once, not just the two anybody remembered", () => {
    const loud = snapshotFixture({ diagnosis: { findings: EVERY_FINDING } });
    mockLoaded(loud);

    renderHealthView();

    expectEveryFindingStatedOnce(loud);
  });

  it("states every finding once for a steady community with a single held session", () => {
    // The card has no median gap to state here, and the ON_RHYTHM finding always fires for a steady
    // snapshot, so this is where a status sentence on the card would collide with its finding.
    const first = snapshotFixture({ diagnosis: { findings: EVERY_FINDING } });
    first.cadence = {
      status: "STEADY",
      sessionsHeld: 1,
      medianGapDays: null,
      trend: { previous: 0, direction: "UP" },
    };
    mockLoaded(first);

    const alive = renderChapter("alive");

    expect(alive.getByText("On rhythm")).toBeInTheDocument();
    expect(screen.queryByText(/median gap/i)).not.toBeInTheDocument();
    expectEveryFindingStatedOnce(first);
  });

  it("states every finding once for a dormant community, and claims no live rhythm", () => {
    // A dormant community still carries whatever median its held dates produce, so the card must
    // state the window as a fact and never as a rhythm that is still running.
    const quiet = snapshotFixture({ diagnosis: { findings: EVERY_FINDING } });
    quiet.cadence = {
      status: "DORMANT",
      sessionsHeld: 2,
      medianGapDays: 14,
      trend: { previous: 4, direction: "DOWN" },
    };
    mockLoaded(quiet);

    const alive = renderChapter("alive");

    expect(alive.getByText("Dormant")).toBeInTheDocument();
    expect(
      screen.getByText("Median gap of 14 days between sessions."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/every 14 days/i)).not.toBeInTheDocument();
    expectEveryFindingStatedOnce(quiet);
  });

  it("renders each finding inside the chapter that shows its evidence", () => {
    mockLoaded(
      snapshotFixture({
        busFactor: 1,
        diagnosis: {
          findings: [
            { kind: "RISK", code: "SINGLE_POINT" },
            { kind: "STRENGTH", code: "KNOWLEDGE_REUSED" },
          ],
        },
      }),
    );

    const { container } = renderHealthView();

    expect(container.querySelector("#carried")).toHaveTextContent(
      "Everything leans on one person",
    );
    expect(container.querySelector("#payoff")).toHaveTextContent(
      "Knowledge is being reused",
    );
  });

  it("has no separate diagnosis panel", () => {
    mockLoaded(snapshotFixture());

    renderHealthView();

    expect(screen.queryByText("What this says")).not.toBeInTheDocument();
  });
});
