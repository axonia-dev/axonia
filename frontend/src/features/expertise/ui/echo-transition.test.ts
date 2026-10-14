import { describe, expect, it } from "vitest";

import { transitionFor } from "@/features/expertise/ui/echo-transition";

const hit = {
  tags: new Set(["kafka"]),
  hits: new Set<number>(),
  lit: new Set([1]),
};
const none = {
  tags: new Set<string>(),
  hits: new Set<number>(),
  lit: new Set<number>(),
};

describe("transitionFor", () => {
  it("snaps when the term did not change, which is the panel moving the highlight", () => {
    expect(
      transitionFor({ from: "kafka", to: "kafka", found: hit, reduced: false }),
    ).toBe("snap");
  });
  it("echoes a new term that matched something", () => {
    expect(
      transitionFor({ from: "", to: "kafka", found: hit, reduced: false }),
    ).toBe("echo");
    expect(
      transitionFor({ from: "kaf", to: "kafka", found: hit, reduced: false }),
    ).toBe("echo");
  });
  it("fades when the term is cleared, or under reduced motion", () => {
    expect(
      transitionFor({ from: "kafka", to: "", found: null, reduced: false }),
    ).toBe("fade");
    expect(
      transitionFor({ from: "", to: "kafka", found: hit, reduced: true }),
    ).toBe("fade");
  });
  it("snaps a first term that matched nothing, since the map does not change", () => {
    expect(
      transitionFor({ from: "", to: "zzz", found: none, reduced: false }),
    ).toBe("snap");
  });
  /** The term before it may still be mid-echo: going back to the whole map is undoing a search, which fades. */
  it("fades a term that stopped matching", () => {
    expect(
      transitionFor({
        from: "kafka",
        to: "kafkaz",
        found: none,
        reduced: false,
      }),
    ).toBe("fade");
  });
});
