import { describe, expect, it } from "vitest";

import type { HealthSnapshot } from "@/features/health/domain/health";
import {
  learningFixture,
  snapshotFixture,
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

function withPayoff(
  snapshot: HealthSnapshot,
  status: HealthStatus,
): HealthSnapshot {
  return {
    ...snapshot,
    reuse: { ...snapshot.reuse, status },
    payoff: status,
  };
}

function withLearningPayoff(
  snapshot: HealthSnapshot,
  status: HealthStatus,
): HealthSnapshot {
  return {
    ...snapshot,
    learning: { ...learningFixture, status },
    payoff: status,
  };
}

describe("summaryOf, on what attendees report", () => {
  it.each<[HealthStatus, string, string]>([
    [
      { code: "LEARNING_A_LOT", level: "GREAT" },
      "Attendees report learning a lot.",
      "learning a lot",
    ],
    [
      { code: "LEARNING", level: "GOOD" },
      "Attendees report learning.",
      "learning",
    ],
    [
      { code: "LITTLE_LEARNED", level: "WATCH" },
      "Attendees report little learned.",
      "little learned",
    ],
  ])(
    "closes on what attendees report when the payoff comes from %j",
    (status, closing, emphasised) => {
      // given
      const snapshot = withLearningPayoff(snapshotFixture(), status);

      // when
      const actual = summaryOf(snapshot);

      // then
      expect(actual.endsWith(` ${closing}`)).toBe(true);
      expect(actual).not.toContain("Library");
      expect(emphasisedIn(snapshot).at(-1)).toBe(emphasised);
    },
  );

  it("keys the payoff clause on the combined payoff, not on reuse alone", () => {
    // given
    const snapshot = withLearningPayoff(snapshotFixture(), {
      code: "LEARNING",
      level: "GOOD",
    });

    // when
    const actual = summaryOf(snapshot);

    // then
    expect(snapshot.reuse.status.code).toBe("NO_PAYOFF_YET");
    expect(actual).not.toContain("has not paid off yet");
  });

  it("falls back to the reuse clause when the payoff is a learning reading with nothing to say", () => {
    // given
    const snapshot: HealthSnapshot = {
      ...snapshotFixture(),
      payoff: { code: "LEARNING_TOO_FEW", level: "QUIET" },
    };

    // when
    const actual = summaryOf(snapshot);

    // then
    expect(actual).toMatch(
      / Library knowledge is being opened but has not paid off yet\.$/,
    );
  });

  it.each<[HealthStatus, string]>([
    [{ code: "LEARNING_A_LOT", level: "GREAT" }, "learning a lot"],
    [{ code: "LEARNING", level: "GOOD" }, "learning"],
    [{ code: "LITTLE_LEARNED", level: "WATCH" }, "little learned"],
  ])(
    "names what attendees report after the reuse clause when reuse wins with %j",
    (learningStatus, emphasised) => {
      // given
      const reuseWins = withPayoff(snapshotFixture(), {
        code: "PAYING_OFF",
        level: "GREAT",
      });
      const snapshot: HealthSnapshot = {
        ...reuseWins,
        learning: { ...learningFixture, status: learningStatus },
      };

      // when
      const actual = summaryOf(snapshot);

      // then
      expect(
        actual.endsWith(
          ` Library knowledge is paying off. Attendees report ${emphasised}.`,
        ),
      ).toBe(true);
      expect(emphasisedIn(snapshot).at(-1)).toBe(emphasised);
    },
  );

  it("adds nothing after the reuse clause while the room is quiet", () => {
    // given
    const snapshot = withPayoff(snapshotFixture(), {
      code: "PAYING_OFF",
      level: "GREAT",
    });

    // when
    const actual = summaryOf(snapshot);

    // then
    expect(snapshot.learning.status.level).toBe("QUIET");
    expect(actual).toMatch(/ Library knowledge is paying off\.$/);
  });
});
