import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  snapshotFixture,
  troubledSnapshotFixture,
} from "@/features/health/domain/snapshot-fixture";
import { ApiError } from "@/lib/api-client";
import {
  mockFailure,
  mockLoaded,
  mockLoading,
  renderHealthView,
} from "@/test/health-view";

vi.mock("@/features/health/api/use-health", () => ({
  useHealthSnapshot: vi.fn(),
}));

beforeEach(() => vi.clearAllMocks());

describe("HealthView", () => {
  it("shows a panel per question", () => {
    // given
    mockLoaded(snapshotFixture());

    // when
    renderHealthView();

    // then
    for (const name of ["Who carries it", "Is it alive", "Is it paying off"]) {
      expect(screen.getByRole("heading", { name })).toBeInTheDocument();
    }
  });

  it("opens on the summary of the three statuses", () => {
    // given
    mockLoaded(snapshotFixture());

    // when
    renderHealthView();

    // then
    expect(
      screen.getByText(
        (_, element) =>
          element?.tagName === "P" &&
          element.textContent ===
            "Meeting about monthly, with 9 different speakers from 7 teams. Library knowledge is being opened but has not paid off yet.",
      ),
    ).toBeVisible();
    expect(screen.getByText("about monthly").tagName).toBe("STRONG");
  });

  it("shows the glance row and the activity chart", () => {
    // given
    mockLoaded(snapshotFixture());

    // when
    renderHealthView();

    // then
    expect(screen.getByRole("article", { name: "Sessions" })).toBeVisible();
    expect(screen.getByRole("heading", { name: "Activity" })).toBeVisible();
  });

  it("never says bus factor or participation", () => {
    // given
    mockLoaded(snapshotFixture());

    // when
    renderHealthView();

    // then
    expect(screen.queryByText(/bus factor/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/participation/i)).not.toBeInTheDocument();
  });

  it("dates its own header from the snapshot it is showing", () => {
    // given
    mockLoaded(snapshotFixture({ window: "180d" }));

    // when
    renderHealthView();

    // then
    expect(
      screen.getByText("Last 180 days, compared with the 180 days before"),
    ).toBeInTheDocument();
  });

  it("offers a dormant community its one next move", () => {
    // given
    const dormant = troubledSnapshotFixture();
    dormant.cadence = {
      ...dormant.cadence,
      rhythm: "DORMANT",
      sessionsHeld: 0,
      status: { code: "DORMANT", level: "WORRY" },
    };
    mockLoaded(dormant);

    // when
    renderHealthView();

    // then
    expect(
      screen.getByRole("link", { name: "Schedule a session" }),
    ).toBeInTheDocument();
  });

  it("keeps the next move away from a community that is alive", () => {
    // given
    mockLoaded(troubledSnapshotFixture());

    // when
    renderHealthView();

    // then
    expect(
      screen.queryByRole("link", { name: "Schedule a session" }),
    ).not.toBeInTheDocument();
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
