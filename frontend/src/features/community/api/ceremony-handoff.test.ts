import { describe, expect, it, vi } from "vitest";

import {
  consumeCeremony,
  handOffCeremony,
  pendingCeremony,
} from "@/features/community/api/ceremony-handoff";

const KEY = "axonia.ceremony";

describe("ceremony handoff", () => {
  it("answers the ceremony handed off for a community", () => {
    // given
    handOffCeremony("welcome", "backend-guild", 1_000);

    // when
    const actual = pendingCeremony("backend-guild", 2_000);

    // then
    expect(actual).toBe("welcome");
  });

  it("answers nothing for another community and keeps the entry", () => {
    // given
    handOffCeremony("birth", "backend-guild", 1_000);

    // when
    const actual = pendingCeremony("design-circle", 2_000);

    // then
    expect(actual).toBeNull();
    expect(sessionStorage.getItem(KEY)).not.toBeNull();
  });

  it("drops an entry older than a minute", () => {
    // given
    handOffCeremony("welcome", "backend-guild", 1_000);

    // when
    const actual = pendingCeremony("backend-guild", 61_001);

    // then
    expect(actual).toBeNull();
    expect(sessionStorage.getItem(KEY)).toBeNull();
  });

  it("drops a malformed entry", () => {
    // given
    sessionStorage.setItem(KEY, "{not json");

    // when
    const actual = pendingCeremony("backend-guild");

    // then
    expect(actual).toBeNull();
    expect(sessionStorage.getItem(KEY)).toBeNull();
  });

  it("is gone once consumed", () => {
    // given
    handOffCeremony("welcome", "backend-guild");

    // when
    consumeCeremony("backend-guild");

    // then
    expect(pendingCeremony("backend-guild")).toBeNull();
  });

  it("keeps another community's entry when consuming", () => {
    // given
    handOffCeremony("welcome", "backend-guild");

    // when
    consumeCeremony("design-circle");

    // then
    expect(pendingCeremony("backend-guild")).toBe("welcome");
  });

  it("hands off nothing, silently, where storage refuses", () => {
    // given
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("quota");
    });

    // when
    const actual = () => handOffCeremony("welcome", "backend-guild");

    // then
    expect(actual).not.toThrow();
    vi.restoreAllMocks();
  });
});
