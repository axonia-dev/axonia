import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import {
  snapshotFixture,
  troubledSnapshotFixture,
} from "@/features/health/domain/snapshot-fixture";
import { GlanceRow } from "@/features/health/ui/GlanceRow";

function tile(name: string) {
  return within(screen.getByRole("article", { name }));
}

describe("GlanceRow", () => {
  it("reads sessions with the rhythm in words", () => {
    // when
    render(<GlanceRow snapshot={snapshotFixture()} />);

    // then
    const sessions = tile("Sessions");
    expect(sessions.getByText("3")).toBeInTheDocument();
    expect(sessions.getByText("same as before")).toBeInTheDocument();
    expect(sessions.getByText("About monthly")).toHaveAttribute(
      "data-level",
      "GOOD",
    );
  });

  it("reads speakers with the stage pill", () => {
    // when
    render(<GlanceRow snapshot={snapshotFixture()} />);

    // then
    const speakers = tile("Speakers");
    expect(speakers.getByText("9")).toBeInTheDocument();
    expect(speakers.getByText("up from 6")).toBeInTheDocument();
    expect(speakers.getByText("Shared stage")).toHaveAttribute(
      "data-level",
      "GREAT",
    );
  });

  it("reads people involved as a count with its members, never a share", () => {
    // when
    render(<GlanceRow snapshot={snapshotFixture()} />);

    // then
    const people = tile("People involved");
    expect(people.getByText("15")).toBeInTheDocument();
    expect(people.getByText("2 of them members")).toBeInTheDocument();
    expect(screen.queryByText(/%/)).not.toBeInTheDocument();
  });

  it("reads library openings with the payoff pill", () => {
    // when
    render(<GlanceRow snapshot={snapshotFixture()} />);

    // then
    const openings = tile("Library openings");
    expect(openings.getByText("2")).toBeInTheDocument();
    expect(openings.getByText("No payoff yet")).toHaveAttribute(
      "data-level",
      "QUIET",
    );
  });

  it("says all members when everybody involved is one", () => {
    // when
    render(<GlanceRow snapshot={troubledSnapshotFixture()} />);

    // then
    expect(tile("People involved").getByText("all members")).toBeVisible();
  });

  it("says none of them members when nobody involved is one", () => {
    // given
    const snapshot = snapshotFixture();
    snapshot.people = { ...snapshot.people, members: 0 };

    // when
    render(<GlanceRow snapshot={snapshot} />);

    // then
    expect(
      tile("People involved").getByText("none of them members"),
    ).toBeVisible();
  });

  it("says one of them a member in the singular", () => {
    // given
    const snapshot = snapshotFixture();
    snapshot.people = { ...snapshot.people, members: 1 };

    // when
    render(<GlanceRow snapshot={snapshot} />);

    // then
    expect(
      tile("People involved").getByText("1 of them a member"),
    ).toBeVisible();
  });

  it("says nobody yet rather than members when nobody was involved", () => {
    // given
    const snapshot = snapshotFixture();
    snapshot.people = {
      involved: 0,
      members: 0,
      trend: { previous: 0, direction: "FLAT" },
    };

    // when
    render(<GlanceRow snapshot={snapshot} />);

    // then
    const people = tile("People involved");
    expect(people.getByText("Nobody yet")).toBeVisible();
    expect(people.queryByText(/members?$/)).not.toBeInTheDocument();
    expect(people.queryByText("same as before")).not.toBeInTheDocument();
  });

  it("says nobody this window, not yet, after the window before had people", () => {
    // given
    const snapshot = snapshotFixture();
    snapshot.people = {
      involved: 0,
      members: 0,
      trend: { previous: 4, direction: "DOWN" },
    };

    // when
    render(<GlanceRow snapshot={snapshot} />);

    // then
    const people = tile("People involved");
    expect(people.getByText("Nobody this window")).toBeVisible();
    expect(people.queryByText("Nobody yet")).not.toBeInTheDocument();
    expect(people.getByText("down from 4")).toBeInTheDocument();
  });

  it("falls back to the status word when there is no rhythm to name", () => {
    // when
    render(<GlanceRow snapshot={troubledSnapshotFixture()} />);

    // then
    const sessions = tile("Sessions");
    expect(sessions.getByText("Slowing")).toHaveAttribute(
      "data-level",
      "WATCH",
    );
    expect(sessions.getByText("down from 4")).toBeInTheDocument();
  });
});
