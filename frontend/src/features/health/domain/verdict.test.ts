import { describe, expect, it } from "vitest";

import type {
  CadenceStatus,
  HealthSnapshot,
} from "@/features/health/domain/health";
import { snapshotFixture } from "@/features/health/domain/snapshot-fixture";
import { verdictsOf } from "@/features/health/domain/verdict";

const BUS_FACTORS = [0, 1, 2, 3, 12];
const CADENCES: CadenceStatus[] = ["STEADY", "SLOWING", "STALLED", "DORMANT"];
const REUSE = [
  { saves: 0, helped: 0 },
  { saves: 6, helped: 0 },
  { saves: 6, helped: 3 },
];

/** One snapshot per combination of the branches verdictsOf actually distinguishes. */
function everyBranch(): HealthSnapshot[] {
  return BUS_FACTORS.flatMap((busFactor) =>
    CADENCES.flatMap((status) =>
      REUSE.map(({ saves, helped }) => {
        const snapshot = snapshotFixture({ busFactor });
        snapshot.cadence.status = status;
        snapshot.reuse.saves = saves;
        snapshot.reuse.helped = helped;
        return snapshot;
      }),
    ),
  );
}

describe("verdictsOf", () => {
  it("states every branch in three words at most, never a sentence", () => {
    // The rule the strip exists under: let it grow into sentences and it has become the diagnosis
    // panel this design deleted. Measured on what verdictsOf produces, because a fixture of
    // hand-written verdicts only ever measures the fixture.
    for (const snapshot of everyBranch()) {
      for (const verdict of verdictsOf(snapshot)) {
        expect(
          verdict.state.split(" ").length,
          verdict.state,
        ).toBeLessThanOrEqual(3);
      }
    }
  });

  it("returns one verdict per chapter, in chapter order", () => {
    expect(verdictsOf(snapshotFixture()).map((v) => v.chapter)).toEqual([
      "carried",
      "alive",
      "payoff",
    ]);
  });

  it("names a bus factor of one as a person rather than a number", () => {
    const [carried] = verdictsOf(snapshotFixture({ busFactor: 1 }));

    expect(carried?.state).toBe("One person");
    expect(carried?.tone).toBe("warning");
  });

  it("reads no contribution at all as the most serious tone", () => {
    const [carried] = verdictsOf(snapshotFixture({ busFactor: 0 }));

    expect(carried?.state).toBe("Nobody");
    expect(carried?.tone).toBe("danger");
  });

  it("reads a shared load as good news", () => {
    const [carried] = verdictsOf(snapshotFixture({ busFactor: 5 }));

    expect(carried?.state).toBe("5 people");
    expect(carried?.tone).toBe("success");
  });

  it("still reads two people as concentrated rather than shared", () => {
    const [carried] = verdictsOf(snapshotFixture({ busFactor: 2 }));

    expect(carried?.state).toBe("2 people");
    expect(carried?.tone).toBe("warning");
  });

  it("flips to good news the moment a third person joins the load", () => {
    const [carried] = verdictsOf(snapshotFixture({ busFactor: 3 }));

    expect(carried?.state).toBe("3 people");
    expect(carried?.tone).toBe("success");
  });

  it("reads a steady cadence as on rhythm", () => {
    const snapshot = snapshotFixture();
    snapshot.cadence.status = "STEADY";

    expect(verdictsOf(snapshot)[1]).toMatchObject({
      state: "On rhythm",
      tone: "success",
    });
  });

  it("reads a slowing cadence as slowing", () => {
    const snapshot = snapshotFixture();
    snapshot.cadence.status = "SLOWING";

    expect(verdictsOf(snapshot)[1]).toMatchObject({
      state: "Slowing",
      tone: "warning",
    });
  });

  it("reads a stalled cadence as stalled", () => {
    const snapshot = snapshotFixture();
    snapshot.cadence.status = "STALLED";

    expect(verdictsOf(snapshot)[1]).toMatchObject({
      state: "Stalled",
      tone: "warning",
    });
  });

  it("reads a dormant community as dormant", () => {
    const snapshot = snapshotFixture();
    snapshot.cadence.status = "DORMANT";

    expect(verdictsOf(snapshot)[1]).toMatchObject({
      state: "Dormant",
      tone: "danger",
    });
  });

  it("separates knowledge that was wanted from knowledge that was confirmed to help", () => {
    const wanted = snapshotFixture();
    wanted.reuse.saves = 6;
    wanted.reuse.helped = 0;

    expect(verdictsOf(wanted)[2]).toMatchObject({
      state: "Wanted, unproven",
      tone: "neutral",
    });
  });

  it("reads an untouched library as nothing yet rather than as a failure", () => {
    const untouched = snapshotFixture();
    untouched.reuse.saves = 0;
    untouched.reuse.helped = 0;

    expect(verdictsOf(untouched)[2]).toMatchObject({
      state: "Nothing yet",
      tone: "neutral",
    });
  });

  it("reads confirmed help as reused", () => {
    const reused = snapshotFixture();
    reused.reuse.helped = 3;

    expect(verdictsOf(reused)[2]).toMatchObject({
      state: "Reused",
      tone: "success",
    });
  });
});
