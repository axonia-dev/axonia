import { describe, expect, it } from "vitest";

import { participationRead } from "@/features/health/domain/participation";

function dial(activeMembers: number, totalMembers: number) {
  return {
    activeMembers,
    totalMembers,
    share: totalMembers === 0 ? 0 : activeMembers / totalMembers,
    trend: { previous: 0, direction: "UP" as const },
  };
}

describe("participationRead", () => {
  it("shows the share once the denominator can support it", () => {
    expect(participationRead(dial(29, 48))).toEqual({
      value: "60%",
      showTrend: true,
    });
  });

  it("shows the share at exactly six members", () => {
    expect(participationRead(dial(3, 6))).toEqual({
      value: "50%",
      showTrend: true,
    });
  });

  it("drops the share below six members, because one person is worth too much of it", () => {
    expect(participationRead(dial(3, 3))).toEqual({
      value: "3 of 3 members",
      showTrend: false,
    });
  });

  it("drops the share at five members, the last size that cannot support one", () => {
    expect(participationRead(dial(2, 5))).toEqual({
      value: "2 of 5 members",
      showTrend: false,
    });
  });

  it("reads an empty community without dividing by zero", () => {
    expect(participationRead(dial(0, 0))).toEqual({
      value: "0 of 0 members",
      showTrend: false,
    });
  });
});
