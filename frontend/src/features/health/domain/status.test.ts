import { describe, expect, it } from "vitest";

import {
  healthStatusSchema,
  STATUS_WORD,
} from "@/features/health/domain/status";

describe("STATUS_WORD", () => {
  it("names every status in the spec's words", () => {
    expect(STATUS_WORD).toEqual({
      TOO_FEW_TALKS: "Too few talks",
      ONE_VOICE: "One voice",
      LEANS_ON_ONE: "Leans on one",
      SHARED: "Shared",
      SHARED_STAGE: "Shared stage",
      DORMANT: "Dormant",
      STARTING_UP: "Starting up",
      STALLED: "Stalled",
      SLOWING: "Slowing",
      PICKING_UP: "Picking up",
      ON_RHYTHM: "On rhythm",
      PAYING_OFF: "Paying off",
      STARTING_TO_PAY_OFF: "Starting to pay off",
      SAVED_NOT_CONFIRMED: "Saved, not confirmed",
      NO_PAYOFF_YET: "No payoff yet",
      NOTHING_OPENED: "Nothing opened yet",
    });
  });

  it("keeps every word short enough for a pill, never a sentence", () => {
    for (const word of Object.values(STATUS_WORD)) {
      expect(word.split(" ").length, word).toBeLessThanOrEqual(4);
    }
  });
});

describe("healthStatusSchema", () => {
  it("parses a status as a code and a level", () => {
    // when
    const actual = healthStatusSchema.parse({
      code: "LEANS_ON_ONE",
      level: "WATCH",
    });

    // then
    expect(actual).toEqual({ code: "LEANS_ON_ONE", level: "WATCH" });
  });

  it("refuses a level the scale does not know", () => {
    // when
    const actual = healthStatusSchema.safeParse({
      code: "SHARED",
      level: "FINE",
    });

    // then
    expect(actual.success).toBe(false);
  });
});
