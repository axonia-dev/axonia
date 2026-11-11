import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  learningFixture,
  snapshotFixture,
  troubledSnapshotFixture,
} from "@/features/health/domain/snapshot-fixture";
import {
  EVERY_FINDING,
  expectEveryFindingStatedOnce,
  mockLoaded,
  renderHealthView,
  renderPanel,
} from "@/test/health-view";

vi.mock("@/features/health/api/use-health", () => ({
  useHealthSnapshot: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());

describe("HealthView findings", () => {
  it("states every finding exactly once, not just the two anybody remembered", () => {
    // given
    const loud = snapshotFixture({ diagnosis: { findings: EVERY_FINDING } });
    mockLoaded(loud);

    // when
    renderHealthView();

    // then
    expectEveryFindingStatedOnce(loud);
  });

  it("states every finding once for a community in trouble", () => {
    // given
    const troubled = {
      ...troubledSnapshotFixture(),
      diagnosis: { findings: EVERY_FINDING },
    };
    mockLoaded(troubled);

    // when
    renderHealthView();

    // then
    expectEveryFindingStatedOnce(troubled);
  });

  it("states every finding once for a dormant community", () => {
    // given
    const quiet = snapshotFixture({ diagnosis: { findings: EVERY_FINDING } });
    quiet.cadence = {
      ...quiet.cadence,
      rhythm: "DORMANT",
      sessionsHeld: 0,
      medianGapDays: null,
      nextSessionAt: null,
      lastHeldAt: null,
      status: { code: "DORMANT", level: "WORRY" },
    };
    quiet.heldSessionDates = [];
    mockLoaded(quiet);

    // when
    renderHealthView();

    // then
    expectEveryFindingStatedOnce(quiet);
  });

  it("states every finding once for a room that answered", () => {
    // given
    const answered = snapshotFixture({
      diagnosis: { findings: EVERY_FINDING },
      learning: learningFixture,
      payoff: { code: "LEARNING_A_LOT", level: "GREAT" },
    });
    mockLoaded(answered);

    // when
    renderHealthView();

    // then
    expectEveryFindingStatedOnce(answered);
  });

  it("files what the room said under paying off", () => {
    // given
    mockLoaded(
      snapshotFixture({
        diagnosis: { findings: EVERY_FINDING },
        learning: { ...learningFixture, enjoyed: 4.6 },
      }),
    );

    // when
    const payoff = renderPanel("payoff");

    // then
    expect(
      payoff.getByText("Attendees report learning a lot: 8.3 out of 10."),
    ).toBeVisible();
    expect(
      payoff.getByText("Sessions are enjoyed more than they teach."),
    ).toBeVisible();
    expect(
      payoff.getByText("Sessions are not landing: enjoyed 4.6 out of 10."),
    ).toBeVisible();
  });

  it("files curation leaning on one person under who carries it", () => {
    // given
    mockLoaded(snapshotFixture({ diagnosis: { findings: EVERY_FINDING } }));

    // when
    const carried = renderPanel("carried");

    // then
    expect(
      carried.getByText(
        "Curation leans on one person: 90% of topics and Library additions.",
      ),
    ).toBeVisible();
  });

  it("renders each finding inside the panel that shows its evidence", () => {
    // given
    mockLoaded(
      snapshotFixture({
        diagnosis: {
          findings: [
            { kind: "RISK", code: "SINGLE_POINT" },
            { kind: "STRENGTH", code: "ON_RHYTHM" },
            { kind: "STRENGTH", code: "KNOWLEDGE_REUSED" },
          ],
        },
      }),
    );

    // when
    const { container } = renderHealthView();

    // then
    expect(container.querySelector("#carried")).toHaveTextContent(
      "Every talk came from one person.",
    );
    expect(container.querySelector("#alive")).toHaveTextContent(
      "Sessions keep a steady rhythm.",
    );
    expect(container.querySelector("#payoff")).toHaveTextContent(
      "Knowledge is being reused",
    );
  });

  it("has no separate diagnosis panel", () => {
    // given
    mockLoaded(snapshotFixture());

    // when
    renderHealthView();

    // then
    expect(screen.queryByText("What this says")).not.toBeInTheDocument();
  });
});
