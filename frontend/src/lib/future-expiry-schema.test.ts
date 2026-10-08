import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  futureExpirySchema,
  optionalFutureExpirySchema,
} from "./future-expiry-schema";

describe("futureExpirySchema", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-15T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("accepts an instant in the future", () => {
    const result = futureExpirySchema.safeParse("2027-01-01T23:59:59Z");
    expect(result.success).toBe(true);
  });

  it("refuses a blank value, naming the field", () => {
    const result = futureExpirySchema.safeParse("");
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe("Expiry date is required");
  });

  it("refuses an instant in the past, naming the field", () => {
    const result = futureExpirySchema.safeParse("2020-01-01T23:59:59Z");
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe(
      "Expiry date must be in the future",
    );
  });

  it("accepts an absent optional expiry, which means until revoked", () => {
    expect(optionalFutureExpirySchema.safeParse(null).success).toBe(true);
    expect(optionalFutureExpirySchema.safeParse(undefined).success).toBe(true);
  });

  it("reads a blank optional expiry as absent, not as an unparseable date", () => {
    const result = optionalFutureExpirySchema.safeParse("");
    expect(result.success).toBe(true);
    expect(result.data).toBeNull();
  });

  it("accepts a future optional expiry", () => {
    const result = optionalFutureExpirySchema.safeParse("2027-01-01T23:59:59Z");
    expect(result.success).toBe(true);
  });

  it("refuses a past optional expiry, which would mint a dead credential", () => {
    const result = optionalFutureExpirySchema.safeParse("2020-01-01T23:59:59Z");
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe(
      "Expiry date must be in the future",
    );
  });
});
