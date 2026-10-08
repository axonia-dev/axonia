import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { todayIso } from "./today-iso";

describe("todayIso", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("reports the current calendar date as an ISO YYYY-MM-DD string", () => {
    vi.setSystemTime(new Date("2026-08-15T12:00:00Z"));
    expect(todayIso()).toBe("2026-08-15");
  });
});
