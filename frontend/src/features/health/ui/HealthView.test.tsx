import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { snapshotFixture } from "@/features/health/domain/snapshot-fixture";
import { ApiError } from "@/lib/api-client";
import {
  mockFailure,
  mockLoaded,
  mockLoading,
  renderChapter,
  renderHealthView,
} from "@/test/health-view";

vi.mock("@/features/health/api/use-health", () => ({
  useHealthSnapshot: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());

describe("HealthView", () => {
  it("shows all three chapters", () => {
    mockLoaded(snapshotFixture());

    renderHealthView();

    expect(
      screen.getByRole("heading", { name: "Who carries it" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Is it alive" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Is it paying off" }),
    ).toBeInTheDocument();
  });

  it("labels the fragility sparkline as contributors and never as the bus factor", () => {
    mockLoaded(snapshotFixture());

    renderHealthView();

    expect(
      screen.getByRole("img", { name: /contributors/ }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("img", { name: /bus factor/ }),
    ).not.toBeInTheDocument();
  });

  it("draws one sparkline per dial, none of them claiming to be the dial itself", () => {
    mockLoaded(snapshotFixture());

    renderHealthView();

    expect(
      screen.getByRole("img", { name: "members engaged per bucket" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "loves per bucket" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "joins per bucket" }),
    ).toBeInTheDocument();
  });

  it("dates its own header from the snapshot it is showing", () => {
    mockLoaded(snapshotFixture({ window: "180d" }));

    renderHealthView();

    expect(screen.getByText("vs. the previous 180 days")).toBeInTheDocument();
  });

  it("reads the three dials from the snapshot", () => {
    mockLoaded(snapshotFixture());

    renderHealthView();

    expect(screen.getByText("40%")).toBeInTheDocument();
    expect(screen.getByText("41 loves")).toBeInTheDocument();
    expect(screen.getByText("80 members")).toBeInTheDocument();
    expect(screen.getByText("5 joined this window")).toBeInTheDocument();
  });

  it("puts the membership trend beside the joins it compares, not beside the member count", () => {
    mockLoaded(snapshotFixture());

    renderHealthView();

    // "80 members, up from 2" is the mislabel this guards: the chip compares joins.
    expect(screen.getByText(/5 joined this window/)).toHaveTextContent(
      "5 joined this window ↑ from 2",
    );
    expect(screen.getByText("80 members").parentElement).not.toHaveTextContent(
      "from 2",
    );
  });

  it("offers the quiet community its one next move", () => {
    const dormant = snapshotFixture({ busFactor: 0 });
    dormant.cadence = {
      status: "DORMANT",
      sessionsHeld: 0,
      medianGapDays: null,
      trend: { previous: 0, direction: "FLAT" },
    };
    dormant.heldSessionDates = [];
    mockLoaded(dormant);

    const alive = renderChapter("alive");

    expect(
      screen.getByRole("link", { name: "Schedule a session" }),
    ).toBeInTheDocument();
    expect(alive.getByText("Dormant")).toBeInTheDocument();
    expect(screen.queryByText(/median gap/i)).not.toBeInTheDocument();
  });

  it("keeps the cadence card singular for a single held session", () => {
    const once = snapshotFixture();
    once.cadence = {
      status: "SLOWING",
      sessionsHeld: 1,
      medianGapDays: null,
      trend: { previous: 4, direction: "DOWN" },
    };
    mockLoaded(once);

    const alive = renderChapter("alive");

    expect(alive.getByText("session held")).toBeInTheDocument();
    expect(alive.getByText("Slowing")).toBeInTheDocument();
  });

  it("keeps the quiet community card away from a community that is alive", () => {
    mockLoaded(snapshotFixture());

    renderHealthView();

    expect(
      screen.queryByRole("link", { name: "Schedule a session" }),
    ).not.toBeInTheDocument();
  });

  it("drops the participation share for a community too small to support one", () => {
    const small = snapshotFixture();
    small.participation = {
      activeMembers: 3,
      totalMembers: 3,
      share: 1,
      trend: { previous: 0, direction: "UP" },
    };
    mockLoaded(small);

    renderHealthView();

    expect(screen.getByText("3 of 3 members")).toBeInTheDocument();
    expect(screen.queryByText("100%")).not.toBeInTheDocument();
  });

  it("states reach is unavailable without an org directory", () => {
    const unsynced = snapshotFixture();
    unsynced.reach = {
      available: false,
      teams: 0,
      departments: 0,
      businessUnits: 0,
      contributorsWithUnit: 0,
      totalContributors: 5,
      trend: { previous: 0, direction: "FLAT" },
    };
    mockLoaded(unsynced);

    renderHealthView();

    expect(screen.getByText(/no org directory is synced/i)).toBeInTheDocument();
  });

  it("explains the organizer gate on a 403", () => {
    // given
    mockFailure(new ApiError(403, "forbidden"));

    // when
    renderHealthView();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(/organizers/i);
  });

  it("falls back to a generic failure message", () => {
    // given
    mockFailure(new ApiError(500, "boom"));

    // when
    renderHealthView();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the health snapshot.",
    );
  });

  it("shows a loading state", () => {
    // given
    mockLoading();

    // when
    renderHealthView();

    // then
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });
});
