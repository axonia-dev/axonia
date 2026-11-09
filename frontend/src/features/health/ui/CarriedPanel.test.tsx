import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { HealthSnapshot } from "@/features/health/domain/health";
import {
  snapshotFixture,
  troubledSnapshotFixture,
} from "@/features/health/domain/snapshot-fixture";
import { CarriedPanel } from "@/features/health/ui/CarriedPanel";
import { factText as fact } from "@/test/health-view";

function withReach(
  reach: Partial<HealthSnapshot["reach"]>,
  snapshot = snapshotFixture(),
): HealthSnapshot {
  return { ...snapshot, reach: { ...snapshot.reach, ...reach } };
}

describe("CarriedPanel", () => {
  it("is headed by its question and judged by the stage pill", () => {
    // when
    render(<CarriedPanel snapshot={snapshotFixture()} />);

    // then
    expect(
      screen.getByRole("heading", { name: "Who carries it" }),
    ).toBeVisible();
    expect(screen.getByText("Shared stage")).toHaveAttribute(
      "data-level",
      "GREAT",
    );
  });

  it("says how many speakers give half the talks", () => {
    // when
    render(<CarriedPanel snapshot={snapshotFixture()} />);

    // then
    expect(fact("speakers give half the talks")).toBe(
      "4 speakers give half the talks",
    );
  });

  it("says one speaker is on stage when there is only one", () => {
    // when
    render(<CarriedPanel snapshot={troubledSnapshotFixture()} />);

    // then
    expect(fact("speaker on stage")).toBe("1 speaker on stage");
  });

  it("says one speaker gives half the talks when one of several does", () => {
    // given
    const snapshot = snapshotFixture();
    snapshot.stage = { ...snapshot.stage, speakersForHalf: 1, speakers: 3 };

    // when
    render(<CarriedPanel snapshot={snapshot} />);

    // then
    expect(fact("speaker gives half the talks")).toBe(
      "1 speaker gives half the talks",
    );
  });

  it("counts talks rather than speakers when there were none", () => {
    // given
    const snapshot = snapshotFixture();
    snapshot.stage = {
      ...snapshot.stage,
      talks: 0,
      speakers: 0,
      speakersForHalf: 0,
      distribution: [],
    };

    // when
    render(<CarriedPanel snapshot={snapshot} />);

    // then
    expect(fact("talks given")).toBe("0 talks given");
    expect(screen.queryByText("On stage")).not.toBeInTheDocument();
  });

  it("counts teams on stage with their trend", () => {
    // when
    render(<CarriedPanel snapshot={snapshotFixture()} />);

    // then
    expect(fact("teams on stage")).toBe("7 up from 5 teams on stage");
  });

  it("singularises one team on stage", () => {
    // when
    render(<CarriedPanel snapshot={troubledSnapshotFixture()} />);

    // then
    expect(screen.getByText("team on stage")).toBeVisible();
  });

  it("names departments and business units only when there are some", () => {
    // when
    render(
      <CarriedPanel
        snapshot={withReach({ departments: 0, businessUnits: 0 })}
      />,
    );

    // then
    expect(screen.queryByText(/department/)).not.toBeInTheDocument();
    expect(screen.queryByText(/business unit/)).not.toBeInTheDocument();
  });

  it("counts departments and business units when there are some", () => {
    // when
    render(<CarriedPanel snapshot={snapshotFixture()} />);

    // then
    expect(
      screen.getByText("3 departments and 2 business units"),
    ).toBeVisible();
  });

  it("names a lone department without business units", () => {
    // when
    render(
      <CarriedPanel
        snapshot={withReach({ departments: 1, businessUnits: 0 })}
      />,
    );

    // then
    expect(screen.getByText("1 department")).toBeVisible();
  });

  it("reads reach unavailable rather than a misleading zero", () => {
    // when
    render(
      <CarriedPanel snapshot={withReach({ available: false, teams: 0 })} />,
    );

    // then
    expect(
      screen.getByText("Teams on stage unavailable: no speaker has team data."),
    ).toBeVisible();
    expect(screen.queryByText(/^teams? on stage$/)).not.toBeInTheDocument();
  });

  it("says nothing about reach when nobody spoke", () => {
    // given
    const snapshot = withReach({ available: false, teams: 0 });
    snapshot.stage = { ...snapshot.stage, talks: 0, speakers: 0 };

    // when
    render(<CarriedPanel snapshot={snapshot} />);

    // then
    expect(screen.queryByText(/unavailable/)).not.toBeInTheDocument();
  });

  it("states its own coverage when the team data is partial", () => {
    // when
    render(
      <CarriedPanel
        snapshot={withReach({ speakersWithUnit: 7, totalSpeakers: 9 })}
      />,
    );

    // then
    expect(screen.getByText("7 of 9 speakers with team data")).toBeVisible();
  });

  it("states no coverage line when every speaker has team data", () => {
    // when
    render(<CarriedPanel snapshot={snapshotFixture()} />);

    // then
    expect(screen.queryByText(/with team data/)).not.toBeInTheDocument();
  });

  it("draws one anonymous segment per speaker", () => {
    // when
    const { container } = render(<CarriedPanel snapshot={snapshotFixture()} />);

    // then
    expect(screen.getByText("9 speakers")).toBeVisible();
    expect(
      container.querySelectorAll("[data-stage-bar='stage'] > *"),
    ).toHaveLength(9);
  });

  it("shows behind the scenes when there are curation acts", () => {
    // when
    render(<CarriedPanel snapshot={snapshotFixture()} />);

    // then
    expect(screen.getByText("Behind the scenes")).toBeVisible();
    expect(screen.getByText("topics and Library")).toBeVisible();
  });

  it("drops behind the scenes when nobody curated anything", () => {
    // given
    const snapshot = snapshotFixture({ curation: { acts: 0, topShare: 0 } });

    // when
    render(<CarriedPanel snapshot={snapshot} />);

    // then
    expect(screen.queryByText("Behind the scenes")).not.toBeInTheDocument();
  });

  it("states its findings beneath its evidence", () => {
    // given
    const snapshot = snapshotFixture({
      diagnosis: {
        findings: [
          { kind: "STRENGTH", code: "WELL_DISTRIBUTED" },
          { kind: "RISK", code: "CURATION_LEANS_ON_ONE" },
          { kind: "STRENGTH", code: "ON_RHYTHM" },
        ],
      },
    });

    // when
    render(<CarriedPanel snapshot={snapshot} />);

    // then
    expect(
      screen.getByText("The stage is shared across 9 speakers from 7 teams."),
    ).toBeVisible();
    expect(
      screen.getByText(
        "Curation leans on one person: 90% of topics and Library additions.",
      ),
    ).toBeVisible();
    expect(
      screen.queryByText("Sessions keep a steady rhythm."),
    ).not.toBeInTheDocument();
  });
});
