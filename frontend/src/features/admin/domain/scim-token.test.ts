import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { generateTokenBodySchema } from "./scim-token";

describe("generateTokenBodySchema", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-15T12:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("accepts a label and a future expiry", () => {
    const result = generateTokenBodySchema.safeParse({
      label: "Entra provisioning",
      expiresAt: "2027-01-01T23:59:59Z",
    });
    expect(result.success).toBe(true);
  });

  it("refuses a blank expiry, naming the field", () => {
    const result = generateTokenBodySchema.safeParse({
      label: "Entra provisioning",
      expiresAt: "",
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe("Expiry date is required");
  });

  it("refuses a past expiry, naming the field", () => {
    const result = generateTokenBodySchema.safeParse({
      label: "Entra provisioning",
      expiresAt: "2020-01-01T23:59:59Z",
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe(
      "Expiry date must be in the future",
    );
  });

  it("refuses a blank label", () => {
    const result = generateTokenBodySchema.safeParse({
      label: "",
      expiresAt: "2027-01-01T23:59:59Z",
    });
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.message).toBe("Label is required");
  });
});
