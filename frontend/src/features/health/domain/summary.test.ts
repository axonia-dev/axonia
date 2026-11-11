import { describe, expect, it } from "vitest";

import type { HealthSnapshot } from "@/features/health/domain/health";
import {
  snapshotFixture,
  troubledSnapshotFixture,
} from "@/features/health/domain/snapshot-fixture";
import type { HealthStatus } from "@/features/health/domain/status";
import { summaryOf as summaryPartsOf } from "@/features/health/domain/summary";

function summaryOf(snapshot: HealthSnapshot): string {
  return summaryPartsOf(snapshot)
    .map(({ text }) => text)
    .join("");
}

function emphasisedIn(snapshot: HealthSnapshot): string[] {
  return summaryPartsOf(snapshot)
    .filter(({ emphasised }) => emphasised)
    .map(({ text }) => text);
}

function withStage(
  snapshot: HealthSnapshot,
  stage: Partial<HealthSnapshot["stage"]>,
): HealthSnapshot {
  return { ...snapshot, stage: { ...snapshot.stage, ...stage } };
}

function withAlive(
  snapshot: HealthSnapshot,
  status: HealthStatus,
  medianGapDays: number | null = snapshot.cadence.medianGapDays,
): HealthSnapshot {
  return {
    ...snapshot,
    cadence: { ...snapshot.cadence, status, medianGapDays },
  };
}

function withPayoff(
  snapshot: HealthSnapshot,
  status: HealthStatus,
  helped = snapshot.reuse.helped,
): HealthSnapshot {
  return {
    ...snapshot,
    reuse: { ...snapshot.reuse, status, helped },
    payoff: status,
  };
}

describe("summaryOf", () => {
  it("emphasises the rhythm and the speakers of a healthy community", () => {
    expect(emphasisedIn(snapshotFixture())).toEqual([
      "about monthly",
      "9 different speakers",
    ]);
  });

  it("emphasises what is going wrong in a community in trouble", () => {
    expect(emphasisedIn(troubledSnapshotFixture())).toEqual([
      "slowing down",
      "one person",
    ]);
  });

  it("summarises a healthy community in the spec's words", () => {
    // when
    const actual = summaryOf(snapshotFixture());

    // then
    expect(actual).toBe(
      "Meeting about monthly, with 9 different speakers from 7 teams. Library knowledge is being opened but has not paid off yet.",
    );
  });

  it("summarises a community in trouble in the spec's words", () => {
    // when
    const actual = summaryOf(troubledSnapshotFixture());

    // then
    expect(actual).toBe(
      "Sessions are slowing down and one person gave every talk. Nothing from the Library has been opened.",
    );
  });

  it("names no teams when reach is unavailable", () => {
    // given
    const snapshot = snapshotFixture();
    snapshot.reach = { ...snapshot.reach, available: false };

    // when
    const actual = summaryOf(snapshot);

    // then
    expect(actual).toMatch(
      /^Meeting about monthly, with 9 different speakers\./,
    );
  });

  it("reads a top speaker with most of the talks", () => {
    // given
    const snapshot = withStage(snapshotFixture(), {
      status: { code: "LEANS_ON_ONE", level: "WATCH" },
    });

    // when
    const actual = summaryOf(snapshot);

    // then
    expect(actual).toMatch(
      /^Meeting about monthly and one speaker gave most of the talks\./,
    );
  });

  it("reads a window with no talks", () => {
    // given
    const snapshot = withStage(snapshotFixture(), {
      talks: 0,
      status: { code: "TOO_FEW_TALKS", level: "QUIET" },
    });

    // when
    const actual = summaryOf(snapshot);

    // then
    expect(actual).toMatch(/^Meeting about monthly and no talks were given\./);
  });

  it("reads a window with too few talks to judge", () => {
    // given
    const snapshot = withStage(snapshotFixture(), {
      talks: 2,
      status: { code: "TOO_FEW_TALKS", level: "QUIET" },
    });

    // when
    const actual = summaryOf(snapshot);

    // then
    expect(actual).toMatch(
      /^Meeting about monthly and only 2 talks were given\./,
    );
  });

  it("reads a single talk in the singular", () => {
    // given
    const snapshot = withStage(snapshotFixture(), {
      talks: 1,
      status: { code: "TOO_FEW_TALKS", level: "QUIET" },
    });

    // when
    const actual = summaryOf(snapshot);

    // then
    expect(actual).toMatch(/and only 1 talk was given\./);
  });

  it.each<[HealthStatus, number | null, string]>([
    [{ code: "PICKING_UP", level: "GREAT" }, 7, "Meeting about weekly"],
    [{ code: "ON_RHYTHM", level: "GOOD" }, 60, "Meeting every few months"],
    [{ code: "ON_RHYTHM", level: "GOOD" }, null, "Sessions are on rhythm"],
    [{ code: "PICKING_UP", level: "GREAT" }, null, "Sessions are picking up"],
    [{ code: "SLOWING", level: "WATCH" }, 30, "Sessions are slowing down"],
    [{ code: "STALLED", level: "WORRY" }, null, "Sessions have stalled"],
    [
      { code: "STARTING_UP", level: "QUIET" },
      null,
      "The first session is scheduled",
    ],
    [
      { code: "DORMANT", level: "WORRY" },
      null,
      "No sessions are held or scheduled",
    ],
  ])("opens %j with a median of %s as %s", (status, median, opening) => {
    // when
    const actual = summaryOf(withAlive(snapshotFixture(), status, median));

    // then
    expect(actual.startsWith(opening)).toBe(true);
  });

  it.each<[HealthStatus, number, string]>([
    [
      { code: "PAYING_OFF", level: "GREAT" },
      4,
      "Library knowledge is paying off.",
    ],
    [
      { code: "STARTING_TO_PAY_OFF", level: "GOOD" },
      1,
      "Library knowledge is starting to pay off.",
    ],
    [
      { code: "SAVED_NOT_CONFIRMED", level: "WATCH" },
      0,
      "Library knowledge is being saved but has not paid off yet.",
    ],
  ])("closes on %j", (status, helped, closing) => {
    // when
    const actual = summaryOf(withPayoff(snapshotFixture(), status, helped));

    // then
    expect(actual.endsWith(` ${closing}`)).toBe(true);
  });

  it("stays at two sentences at most", () => {
    // when
    const actual = summaryOf(snapshotFixture());

    // then
    expect(actual.split(". ").length).toBeLessThanOrEqual(2);
  });
});
